package com.github.nopecho.example.circuitbreaker

import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = RANDOM_PORT)
class CircuitControllerTest(@LocalServerPort val port: Int) {

    @Test
    fun `서킷 없이 호출`() {
        Given {
            port(port)
            contentType(MediaType.APPLICATION_JSON_VALUE)
        }.When {
            log().all()
            get("/normal-call")
        }.Then {
            log().all()
        }
    }
}