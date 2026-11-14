package com.sunrun.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LambdaHelpersInitTest {
    @BeforeEach
    fun reset() {
        resetSecurityLoggingForTests()
        resetLambdaSecurityLogging()
    }

    @Test
    fun initLambdaSecurityLoggingSurvivesInjectedFailure() {
        initLambdaSecurityLoggingTestFailure = { throw IllegalStateException("simulated cold-start init failure") }
        assertDoesNotThrow {
            initLambdaSecurityLogging(
                serviceName = "a-la-carte-pricing",
                envs = listOf(LambdaEnv(listOf("devmaj"), CloudEnvType.DEV, "devmaj", "563116987804")),
                defaultAccountId = "563116987804",
                testMode = true,
            )
        }
        initLambdaSecurityLoggingTestFailure = null
        initLambdaSecurityLogging(
            serviceName = "a-la-carte-pricing",
            envs = listOf(LambdaEnv(listOf("devmaj"), CloudEnvType.DEV, "devmaj", "563116987804")),
            defaultAccountId = "563116987804",
            testMode = true,
        )
        assertEquals("a-la-carte-pricing", getEnvironmentConfig()["service_name"])
    }
}
