package com.github.nopecho.example.circuitbreaker

import org.springframework.stereotype.Component
import java.lang.Math.random

@Component
class SomethingExternalApi {

    fun callExternalApi(
        latencyMs: Long = 500,
        failureRate: Double = 0.5
    ): String {
        Thread.sleep(latencyMs)
        if (random() < failureRate) {
            throw RuntimeException("External API call failed")
        }
        return "ok"
    }
}