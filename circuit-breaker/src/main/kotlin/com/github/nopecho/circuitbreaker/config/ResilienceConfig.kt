package com.github.nopecho.circuitbreaker.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration


@Configuration
class ResilienceConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val defaultConfig = defaultCircuitBreakerConfig()
        return CircuitBreakerRegistry.of(defaultConfig)
            .apply {
                val myApiCircuitConfig = CircuitBreakerConfig.from(defaultConfig)
                    .slowCallDurationThreshold(Duration.ofSeconds(3))
                    .slowCallRateThreshold(100f)
                    .build()
                addConfiguration(MY_API_CIRCUIT_BREAKER, myApiCircuitConfig)
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