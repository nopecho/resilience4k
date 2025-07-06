package com.github.nopecho.circuitbreaker.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration


@Configuration
class ResilienceConfig {

    companion object {
        const val DEFAULT_CIRCUIT_BREAKER = "defaultCircuitBreaker"
        const val MY_API_CIRCUIT_BREAKER = "myApiCircuitBreaker"
    }

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val defaultConfig = defaultCircuitBreakerConfig()
        return CircuitBreakerRegistry.of(defaultConfig)
            .apply { registerAllCircuitBreakers(defaultConfig) }
            .apply { attachAllTransitionLoggingProcessor() }
    }

    /**
     * 기본 설정을 기반으로 모든 CircuitBreaker를 등록합니다.
     *
     * 이 함수는 표준 CircuitBreaker와 특정 API에 대한 CircuitBreaker를 생성하고,
     * 이를 CircuitBreakerRegistry에 등록합니다.
     *
     * @receiver CircuitBreaker를 등록할 대상 [CircuitBreakerRegistry].
     * @param baseConfig CircuitBreaker를 생성하는 데 사용할 기본 설정 [CircuitBreakerConfig].
     */
    private fun CircuitBreakerRegistry.registerAllCircuitBreakers(
        baseConfig: CircuitBreakerConfig
    ) {
        circuitBreaker(DEFAULT_CIRCUIT_BREAKER, baseConfig)
        registerMyApiCircuitBreaker(baseConfig)
    }

    private fun CircuitBreakerRegistry.registerMyApiCircuitBreaker(
        baseConfig: CircuitBreakerConfig
    ) {
        val myApiCircuitConfig = CircuitBreakerConfig.from(baseConfig)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .slowCallRateThreshold(100f)
            .build()
        addConfiguration(MY_API_CIRCUIT_BREAKER, myApiCircuitConfig)
        circuitBreaker(MY_API_CIRCUIT_BREAKER, myApiCircuitConfig)
    }

    /**
     * 모든 CircuitBreaker에 대해 상태 전이 로그를 남기는 프로세서를 연결합니다.
     *
     * 이 메소드는 `CircuitBreakerRegistry`에 등록된 모든 CircuitBreaker의 상태 전이 이벤트를 감지하여
     * 로그를 기록할 수 있도록 이벤트 프로세서를 설정합니다.
     *
     * @receiver `CircuitBreakerRegistry` 상태 전이 로그 프로세서를 등록할 대상 CircuitBreaker 레지스트리.
     */
    private fun CircuitBreakerRegistry.attachAllTransitionLoggingProcessor() {
        allCircuitBreakers.forEach {
            val logger = LoggerFactory.getLogger("circuit-breaker-${it.name}")
            it.eventPublisher.onStateTransition { event ->
                val transition = event.stateTransition
                logger.info("Circuit state transited: [{}] -> [{}]", transition.fromState, transition.toState)
            }
        }
    }

    /**
     * CircuitBreaker의 기본 설정을 생성합니다.
     *
     * 기본 설정에는 다음과 같은 내용이 포함됩니다.
     * - 슬라이딩 창 타입: 호출 수 기반(COUNT_BASED)
     * - 슬라이딩 창 크기: 10
     * - 최소 호출 수: 10
     * - 허용 실패율: 50%
     * - OPEN 상태 유지 시간: 5초
     * - HALF-OPEN 상태에서 허용 호출 수: 3
     * - 느린 호출 비율 임계값: 80%
     * - 느린 호출 지속 시간 임계값: 5초
     *
     * @return 기본 CircuitBreaker 설정을 담은 [CircuitBreakerConfig] 객체
     */
    private fun defaultCircuitBreakerConfig(): CircuitBreakerConfig {
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slowCallRateThreshold(80f)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .build()
    }
}