package com.github.nopecho.example.circuitbreaker

import com.github.nopecho.circuitbreaker.CircuitBreakerService
import com.github.nopecho.circuitbreaker.CircuitBreakerService.Companion.fallback
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CircuitController(
    private val externalApi: SomethingExternalApi,
    private val circuitBreaker: CircuitBreakerService
) {

    @GetMapping("/normal-call")
    fun normalCall(): Map<String, Any> {
        val response = externalApi.callExternalApi()
        return mapOf(
            "result" to response
        )
    }

    @GetMapping("/circuit-call")
    fun circuitCall(
        @RequestParam latency: Long = 500,
    ): Map<String, Any> {
        val response = circuitBreaker.executeWithCircuit {
            externalApi.callExternalApi(latency)
        }.fallback {
            "fallback response!"
        }

        return mapOf(
            "result" to response
        )
    }
}