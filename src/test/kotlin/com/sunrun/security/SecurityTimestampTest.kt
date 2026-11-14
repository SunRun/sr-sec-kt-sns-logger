package com.sunrun.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SecurityTimestampTest {
    private val normalizerTimestamp =
        Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}\+00:00$""")

    @Test
    fun formatSecurityTimestampNowMatchesNormalizerRegex() {
        assertTrue(normalizerTimestamp.matches(formatSecurityTimestamp(Instant.now())))
    }

    @Test
    fun formatSecurityTimestampZeroMicrosPadsSixDigits() {
        val instant = Instant.ofEpochSecond(1_700_000_000L)
        assertEquals("2023-11-14T22:13:20.000000+00:00", formatSecurityTimestamp(instant))
        assertTrue(normalizerTimestamp.matches(formatSecurityTimestamp(instant)))
    }

    @Test
    fun menuPriceRecordAccessCapturedTimestampMatchesNormalizer() {
        resetSecurityLoggingForTests()
        initSecurityLogging(
            testMode = true,
            cloudEnvType = CloudEnvType.DEV,
            cloudEnvUniqueId = "563116987804",
            cloudEnvName = "devmaj",
            serviceAccountId = "arn:aws:lambda:us-west-2:563116987804:function:MenuPriceFn",
            serviceName = "a-la-carte-pricing",
        )
        val env = EnvFields(
            cloudEnvType = CloudEnvType.DEV,
            cloudEnvUniqueId = "563116987804",
            cloudEnvName = "devmaj",
            serviceAccountId = "arn:aws:lambda:us-west-2:563116987804:function:MenuPriceFn",
            serviceName = "a-la-carte-pricing",
        )
        logRecordAccess(
            env = env,
            endpointPath = "/MenuPriceFn",
            dataSensitivityLevel = DataSensitivityLevel.CONFIDENTIAL,
            idList = listOf("line-item-1"),
            detail = Detail.VIEW_RECORD,
            actorIdentifier = "unknown",
            actorType = ActorType.SYSTEM_SELF,
            sessionId = "pre_session",
        )
        val ts = SecurityLoggingState.publisher!!.capturedMessages.single()["timestamp"] as String
        assertTrue(normalizerTimestamp.matches(ts), "timestamp was $ts")
    }
}
