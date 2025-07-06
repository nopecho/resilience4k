package com.github.nopecho.circuitbreaker.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ResilienceConfigTest {

    @Autowired
    private lateinit var sut: CircuitBreakerRegistry

    @Test
    fun `레지스트리가 올바르게 초기화되면 기본 서킷 브레이커가 존재한다`() {
        // given
        val expectedCircuitBreakerName = ResilienceConfig.DEFAULT_CIRCUIT_BREAKER

        // when
        val actual = sut.circuitBreaker(expectedCircuitBreakerName)

        // then
        actual.name shouldBe expectedCircuitBreakerName
    }

    @Test
    fun `레지스트리가 올바르게 초기화되면 사용자 정의 서킷 브레이커가 존재한다`() {
        // given
        val expectedCircuitBreakerName = ResilienceConfig.MY_API_CIRCUIT_BREAKER

        // when
        val actual = sut.circuitBreaker(expectedCircuitBreakerName)

        // then
        actual.name shouldBe expectedCircuitBreakerName
    }

    @Test
    fun `레지스트리가 올바르게 초기화되면 올바른 기본 설정으로 초기화된다`() {
        // given
        val defaultConfig = mockk<CircuitBreakerConfig>(relaxed = true)
        every { defaultConfig.slidingWindowType } returns CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
        every { defaultConfig.slidingWindowSize } returns 10

        sut = CircuitBreakerRegistry.of(defaultConfig)

        // when
        val defaultCircuitBreaker = sut.circuitBreaker(ResilienceConfig.DEFAULT_CIRCUIT_BREAKER)

        // then
        with(defaultCircuitBreaker) {
            name shouldBe ResilienceConfig.DEFAULT_CIRCUIT_BREAKER
            circuitBreakerConfig.slidingWindowType shouldBe CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
            circuitBreakerConfig.slidingWindowSize shouldBe 10
        }
    }
}