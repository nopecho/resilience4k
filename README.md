# Kotlin Resilience4j

코틀린의 함수형 프로그래밍으로 Spring 애플리케이션의 회복력 높이기

## Why?

Spring에서 Resilience4j를 사용할 때 보통 AOP(Aspect-Oriented Programming)를 활용한 어노테이션 기반 접근법을 많이 사용합니다. 예를 들어 `@CircuitBreaker`,
`@Retry` 같은 어노테이션을 메서드에 붙이는 방식이죠.

```java

@CircuitBreaker(name = "backendA")
public String doSomething() {
    return backendAService.doSomething();
}
```

이 방식은 간단하지만 몇 가지 한계가 있습니다:

- 메서드 단위로만 적용 가능
- 동적인 설정 변경이 어려움
- 테스트 작성이 복잡함
- 코드 흐름 파악이 어려움

**Kotlin의 함수형 프로그래밍 기법**을 활용하면 이런 한계를 극복하고 더 유연하고 표현력 있는 방식으로 Resilience4j를 사용할 수 있습니다.

## 함수형 접근의 핵심 요소

이 프로젝트에서는 다음과 같은 Kotlin의 기능을 활용합니다:

- **고차 함수**: 함수를 인자로 받거나 반환하는 함수
- **람다 표현식**: 간결한 함수 정의
- **확장 함수**: 기존 클래스에 새 기능 추가
- **Result 타입**: 성공/실패를 명시적으로 처리
- **스코프 함수**: 객체 컨텍스트 내에서 코드 블록 실행

## 함수형 접근의 장점

1. **코드 가독성**: 보호하려는 코드 블록이 명확하게 보임
2. **유연성**: 메서드 내 특정 부분만 보호 가능
3. **조합 가능성**: 여러 회복력 패턴을 쉽게 조합 가능
4. **테스트 용이성**: 함수형 코드는 테스트하기 쉬움
5. **명시적 오류 처리**: Result 타입으로 오류 처리가 명확해짐

## Circuit Breaker

### 기존 AOP 방식의 한계

```kotlin
// 전통적인 AOP 방식
@CircuitBreaker(name = "backendA", fallbackMethod = "fallback")
fun callExternalService(): String {
    return externalService.performAction()
}

fun fallback(e: Exception): String {
    return "대체 응답"
}
```

이 방식은 간단하지만 코드 흐름을 따라가기 어렵고, 특정 코드 블록에만 적용하기 어렵습니다.

### 함수형 접근 방식

```kotlin
// 함수형 접근 방식
fun callExternalService(): String {
    return circuitBreakerManager.executeWithCircuit("myApiCircuitBreaker") {
        // 이 블록만 서킷 브레이커로 보호됨
        externalService.performAction()
    }.fallback {
        // 서킷이 열렸을 때 실행될 대체 로직
        "대체 응답"
    }
}
```

### 구현 살펴보기

핵심은 `CircuitBreakerManager` 클래스입니다:

```kotlin
@Component
class CircuitBreakerManager(
    private val registry: CircuitBreakerRegistry
) {
    // 고차 함수: 함수를 인자로 받음
    fun <T> executeWithCircuit(
        name: String = DEFAULT_CIRCUIT_BREAKER,
        block: () -> T
    ): Result<T> {
        val circuitBreaker = registry.getConfiguration(name).getOrNull()
            ?.let { registry.circuitBreaker(name, it) }
            ?: registry.circuitBreaker(DEFAULT_CIRCUIT_BREAKER)

        // runCatching: Kotlin의 Result 타입을 활용한 예외 처리
        return runCatching {
            circuitBreaker.executeSupplier(block)
        }.onFailure { e ->
            logger.error("Circuit breaker [{}]: failed with exception: {}", name, e.message, e)
        }
    }

    // 확장 함수: Result 타입에 fallback 기능 추가
    companion object {
        internal fun <T> Result<T>.fallback(block: () -> T): T {
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
}
```

### 사용 예제

```kotlin
// 간단한 서킷 브레이커 적용
fun getUser(id: Long): User {
    return circuitBreakerManager.executeWithCircuit {
        userRepository.findById(id)
    }.getOrThrow() // 실패 시 예외 발생
}
```

### 대체 로직(Fallback) 적용

```kotlin
// 대체 로직 적용
fun getProductInfo(id: String): ProductInfo {
    return circuitBreakerManager.executeWithCircuit("productApi") {
        productApiClient.fetchProductInfo(id)
    }.fallback {
        // 서킷이 열렸을 때 캐시에서 가져오기
        productCacheRepository.getProductInfo(id)
    }
}
```

## 마무리

Kotlin의 함수형 프로그래밍 기법을 활용하면 Spring 환경에서 Resilience4j를 더 유연하고 표현력 있게 사용할 수 있습니다. 전통적인 AOP 방식의 한계를 극복하고, 코드의 가독성과 유지보수성을 높일
수 있습니다.

> 이 프로젝트는 현재 서킷 브레이커 구현을 포함하고 있으며, 앞으로 재시도(Retry), 타임아웃(Timeout), 벌크헤드(Bulkhead) 등 다양한 회복력 패턴을 함수형 접근 방식으로 구현할 예정입니다. 
