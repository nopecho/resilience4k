package com.github.nopecho.circuitbreaker

import com.github.nopecho.circuitbreaker.config.DEFAULT_CIRCUIT_BREAKER
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

/**
 * 서킷 브레이커를 관리하고 작업 블록 실행 시 서킷 브레이커를 적용하는 기능을 제공하는 클래스.
 *
 * @property registry 서킷 브레이커를 관리하는 [CircuitBreakerRegistry] 객체.
 * @see io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
 */
@Component
class CircuitBreakerManager(
    private val registry: CircuitBreakerRegistry
) {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * 지정된 이름의 서킷 브레이커를 사용하여 주어진 작업 블록을 실행합니다.
     * 작업 수행 중 예외 발생 시 로깅을 처리합니다.
     *
     * @param name 서킷 브레이커의 이름.
     * @param block 실행할 작업의 블록. 반환값이 있는 함수여야 합니다.
     * @return 작업 실행 결과를 감싸는 [Result] 객체. 성공 시 결과 값을, 실패 시 예외를 포함합니다.
     * @throws CallNotPermittedException 호출이 서킷 브레이커에 의해 차단된 경우 발생.
     *
     * @see io.github.resilience4j.circuitbreaker.CircuitBreaker
     * @see CircuitBreakerManager.fallback
     */
    fun <T> execute(
        name: String = DEFAULT_CIRCUIT_BREAKER,
        block: () -> T
    ): Result<T> {
        val circuitBreaker = registry.getConfiguration(name).getOrNull()
            ?.let { registry.circuitBreaker(name, it) }
            ?: registry.circuitBreaker(DEFAULT_CIRCUIT_BREAKER)

        return runCatching {
            circuitBreaker.executeSupplier(block)
        }.onFailure { exception ->
            logger.error("Circuit breaker [{}]: failed with exception: ${exception.message}", name, exception)
        }
    }

    /**
     * [Result]의 실패 상태를 처리하여 대체 동작을 수행합니다.
     *
     * 이 메서드는 [Result] 객체의 값이 성공적으로 반환된 경우 해당 값을 반환하고,
     * 실패한 경우 예외를 확인하여 서킷이 열려 [CallNotPermittedException]일 때 제공된 [block]을 실행하여 대체 값을 반환합니다.
     * 만약 [CallNotPermittedException]이 아닌 예외가 발생했을 경우 그대로 다시 예외를 던집니다.
     *
     * @param block [CallNotPermittedException]이 발생한 경우 실행할 대체 동작 블록.
     * @return 성공 시 [Result]의 값, 또는 [block]의 결과 값.
     * @throws Throwable [CallNotPermittedException]이 아닌 다른 예외가 발생한 경우 해당 예외를 전달.
     */
    fun <T> Result<T>.fallback(block: () -> T): T {
        return this.fold(
            onSuccess = { it },
            onFailure = { exception ->
                if (exception is CallNotPermittedException) {
                    block()
                } else {
                    throw exception
                }
            }
        )
    }
}