package com.github.nopecho.retry

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RetryApplication

fun main(args: Array<String>) {
    runApplication<RetryApplication>(*args)
}