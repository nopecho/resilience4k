package com.github.nopecho.circuitbreaker

import com.github.nopecho.circuitbreaker.CircuitBreakerManager.Companion.fallback
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class CircuitBreakerManagerTest {

    @Autowired
    private lateinit var sut: CircuitBreakerManager

    @Autowired
    private lateinit var registry: CircuitBreakerRegistry

    private lateinit var testCircuitBreaker: CircuitBreaker

    companion object {
        private const val TEST_CIRCUIT_NAME = "testCircuitBreaker"
    }

    @BeforeEach
    fun setUp() {
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10) // 최근 10번의 호출을 기준으로 계산
            .minimumNumberOfCalls(10) // 서킷 상태를 계산하기 위한 최소 호출 횟수
            .failureRateThreshold(50f) // 실패율 50% 이상이면 서킷 OPEN
            .waitDurationInOpenState(Duration.ofSeconds(3)) // 서킷 OPEN 유지 시간
            .permittedNumberOfCallsInHalfOpenState(4) // HALF_OPEN 상태에서 허용할 호출 수
            .slowCallDurationThreshold(Duration.ofMillis(100)) // 100ms 이상 걸리면 느린 호출로 간주
            .slowCallRateThreshold(80f) // 느린 호출 비율 80% 이상이면 서킷 OPEN
            .build()
            .apply { registry.addConfiguration(TEST_CIRCUIT_NAME, this) }
            .let { registry.circuitBreaker(TEST_CIRCUIT_NAME, it) }
            .apply { this.eventPublisher.onStateTransition { println(it.stateTransition) } }
            .also { testCircuitBreaker = it }
            .also { it.reset() }
    }

    @Nested
    @DisplayName("실패율 및 슬라이딩 윈도우 설정 테스트")
    inner class FailureRateAndWindowSizeTests {

        @Test
        fun `minimumNumberOfCalls(10) 미만으로 실패하면 서킷이 열리지 않아야 한다`() {
            // given: 최소 호출 횟수(10)보다 적은 9번의 실패를 발생시킨다.
            repeat(9) { executeFailure() }

            // then: 실패율이 100%라도 최소 호출 횟수를 만족하지 못했으므로 서킷은 CLOSED 상태여야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        @Test
        fun `minimumNumberOfCalls(10)를 만족하고 failureRateThreshold(50%)에 도달하면 서킷이 열려야 한다`() {
            // given: 최소 호출 횟수(10)를 채우고, 그 중 5번(50%)을 실패시킨다.
            repeat(5) { executeSuccess() }
            repeat(5) { executeFailure() }

            // then: 최소 호출 횟수와 실패율 임계값을 모두 만족했으므로 서킷은 OPEN 상태가 되어야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.OPEN
        }

        @Test
        fun `failureRateThreshold(50%)에 도달하지 않으면 서킷이 열리지 않아야 한다`() {
            // given: 10번 호출 중 4번 실패 (실패율 40%)
            repeat(6) { executeSuccess() }
            repeat(4) { executeFailure() }

            // then: 실패율 임계값(50%) 미만이므로 서킷은 CLOSED 상태를 유지해야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        @Test
        fun `slidingWindowSize(10)에 따라 오래된 호출은 계산에서 제외되어야 한다`() {
            // 실패 4번 추가
            repeat(4) { executeFailure() }
            // 성공 6번 추가
            repeat(6) { executeSuccess() }
            // 현재 상태: xxxxoooooo (실패율 40%) -> 서킷은 CLOSED 상태
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            // 실패 2번 추가
            repeat(2) { executeFailure() }
            // 현재 상태: xxooooooxx (실패율 40%) -> 서킷은 CLOSED 상태
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            // 성공 1번 추가
            repeat(1) { executeSuccess() }
            // 현재 상태: xooooooxxo (실패율 30%) -> 서킷은 CLOSED 상태
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            // 실패 3번 추가
            repeat(3) { executeFailure() }
            // 슬라이딩 윈도우는 가장 최근의 10개 호출만 고려한다.
            // 현재 상태: ooooxxoxxx (실패율 50%) -> 서킷은 OPEN 상태
            testCircuitBreaker.state shouldBe CircuitBreaker.State.OPEN
        }
    }

    @Nested
    @DisplayName("느린 호출 비율 설정 테스트")
    inner class SlowCallRateTests {

        @Test
        fun `slowCallDurationThreshold(100ms)보다 짧은 호출은 느린 호출로 간주하지 않아야 한다`() {
            // given: 10번의 호출이 모두 100ms보다 짧게 걸리도록 한다.
            repeat(10) {
                executeSuccess(sleepMillis = 30)
            }
            // then: 느린 호출이 없으므로 서킷은 CLOSED 상태여야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        @Test
        fun `slowCallRateThreshold(80%) 미만이면 서킷이 열리지 않아야 한다`() {
            // given: 느린 호출(100ms 이상)을 7번(70%), 빠른 호출을 3번 발생시킨다.
            repeat(7) { executeSuccess(sleepMillis = 101) }
            repeat(3) { executeSuccess(sleepMillis = 10) }

            // then: 느린 호출 비율 임계값(80%) 미만이므로 서킷은 CLOSED 상태를 유지해야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        @Test
        fun `slowCallRateThreshold(80%) 이상이면 서킷이 열려야 한다`() {
            // given: 느린 호출(100ms 이상)을 8번(80%), 빠른 호출을 2번 발생시킨다.
            repeat(8) { executeSuccess(sleepMillis = 101) }
            repeat(2) { executeSuccess(sleepMillis = 10) }

            // then: 느린 호출 비율 임계값(80%)에 도달했으므로 서킷은 OPEN 상태가 되어야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.OPEN
        }
    }

    @Nested
    @DisplayName("상태 전이 설정 테스트 (OPEN, HALF_OPEN, CLOSED)")
    inner class StateTransitionTests {

        @Test
        fun `OPEN 상태에서 waitDurationInOpenState(3s) 이후 HALF_OPEN으로 상태가 변경되어야 한다`() {
            // given: 서킷을 강제로 OPEN 상태로 만든다.
            testCircuitBreaker.transitionToOpenState()

            // when: 3초가 지난 후 요청
            Thread.sleep(3000)
            executeSuccess()

            // then: 3초가 지난 후 요청이 들어올 경우 상태가 HALF_OPEN으로 변경된다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN
        }

        @Test
        fun `HALF_OPEN 상태에서 permittedNumberOfCallsInHalfOpenState(4) 만큼 성공하면 서킷이 닫혀야 한다`() {
            // given: 서킷을 HALF_OPEN 상태로 만든다.
            testCircuitBreaker.transitionToOpenState()
            testCircuitBreaker.transitionToHalfOpenState()

            // when: 허용된 호출 횟수(4)만큼 성공시킨다.
            repeat(4) { executeSuccess() }

            // then: 허용된 호출을 모두 성공했으므로 서킷은 CLOSED 상태가 되어야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        @Test
        fun `HALF_OPEN 상태에서 허용된 호출 중 실패 호출이 failureRateThreshold(50%)를 넘기면 서킷이 다시 열린다`() {
            // given: 서킷을 HALF_OPEN 상태로 만든다.
            testCircuitBreaker.transitionToOpenState()
            testCircuitBreaker.transitionToHalfOpenState()

            // when: 허용된 호출 횟수(4) 중 2번 성공, 2번 실패시킨다. 실패율: 50%
            repeat(2) { executeSuccess() }
            repeat(2) { executeFailure() }

            testCircuitBreaker.state shouldBe CircuitBreaker.State.OPEN
        }

        @Test
        fun `HALF_OPEN 상태에서 허용된 호출 중 실패 호출이 failureRateThreshold(50%) 미만이면 서킷이 닫힌다`() {
            // given: 서킷을 HALF_OPEN 상태로 만들고, 한 번의 성공 후 실패를 발생시킨다.
            testCircuitBreaker.transitionToOpenState()
            testCircuitBreaker.transitionToHalfOpenState()

            // when: 허용된 호출 횟수(4) 중 3번 성공, 1번 실패시킨다. 실패율: 25%
            repeat(3) { executeSuccess() }
            repeat(1) { executeFailure() }

            // then: 즉시 OPEN 상태로 돌아가야 한다.
            testCircuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }
    }

    private fun executeSuccess(sleepMillis: Long = 0) {
        sut.executeWithCircuit(TEST_CIRCUIT_NAME) {
            if (sleepMillis > 0) Thread.sleep(sleepMillis)
            "성공"
        }
    }

    private fun executeFailure() {
        shouldThrowAny {
            sut.executeWithCircuit(TEST_CIRCUIT_NAME) {
                throw RuntimeException("실패")
            }.fallback {
                "대체 동작: 실패 처리"
            }
        }
    }
}