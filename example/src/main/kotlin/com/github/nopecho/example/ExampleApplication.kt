package com.github.nopecho.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.github.nopecho"])
class ExampleApplication

fun main(args: Array<String>) {
    runApplication<ExampleApplication>(*args)
}