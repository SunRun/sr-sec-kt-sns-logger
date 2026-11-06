package com.sunrun.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityLoggingTest {
    private val env = EnvFields(
        cloudEnvType = CloudEnvType.STAGE,
        cloudEnvUniqueId = "123456789012",
        cloudEnvName = "devmaj",
        serviceAccountId = "arn:aws:lambda:us-west-2:123456789012:function:a-la-carte-pricing",
        serviceName = "a-la-carte-pricing",
    )

    @BeforeEach
    fun setup() {
        resetSecurityLoggingForTests()
        resetLambdaSecurityLogging()
        initSecurityLogging(
            testMode = true,
            cloudEnvType = env.cloudEnvType,
            cloudEnvUniqueId = env.cloudEnvUniqueId,
            cloudEnvName = env.cloudEnvName,
            serviceAccountId = env.serviceAccountId,
            serviceName = env.serviceName,
        )
    }

    @Test
    fun recordAccessPayloadMatchesNormalizerFields() {
        val result = logRecordAccess(
            env = env,
            endpointPath = "/allocations",
            dataSensitivityLevel = DataSensitivityLevel.CONFIDENTIAL,
            idList = listOf("prospect-1", "prospect-2"),
            detail = Detail.VIEW_RECORD,
            status = Status.SUCCESS,
            actorIdentifier = "unknown",
            actorType = ActorType.SYSTEM_SELF,
            sessionId = "pre_session",
        )
        assertEquals("success", result["status"])
        val event = SecurityLoggingState.publisher!!.capturedMessages.single()
        assertEquals(EventType.RECORD_ACCESS, event["event_type"])
        assertEquals(LogCategory.CUSTOMER_DATA_ACTIONS, event["log_category"])
        assertEquals(Status.SUCCESS, event["status"])
        assertEquals("/allocations", event["endpoint_path"])
        assertEquals(DataSensitivityLevel.CONFIDENTIAL, event["data_sensitivity_level"])
        assertEquals(listOf("prospect-1", "prospect-2"), event["id_list"])
        assertEquals(2, event["record_count"])
        assertEquals(Detail.VIEW_RECORD, event["detail"])
        assertEquals("pre_session", event["session_id"])
    }

    @Test
    fun recordAccessFailureStatusIsPreserved() {
        logRecordAccess(
            env = env,
            endpointPath = "/allocations",
            dataSensitivityLevel = DataSensitivityLevel.CONFIDENTIAL,
            idList = listOf("prospect-1"),
            detail = Detail.VIEW_RECORD,
            status = Status.FAILURE,
            actorIdentifier = "unknown",
            actorType = ActorType.SYSTEM_SELF,
            sessionId = "unknown",
        )
        val event = SecurityLoggingState.publisher!!.capturedMessages.single()
        assertEquals(Status.FAILURE, event["status"])
        assertEquals(1, event["record_count"])
    }

    @Test
    fun invalidEventTypeDoesNotPublish() {
        val result = logRecordAccess(
            env = env,
            eventType = "not-a-real-event",
            endpointPath = "/allocations",
            dataSensitivityLevel = DataSensitivityLevel.CONFIDENTIAL,
            idList = listOf("prospect-1"),
            detail = Detail.VIEW_RECORD,
            actorIdentifier = "unknown",
            actorType = ActorType.SYSTEM_SELF,
            sessionId = "unknown",
        )
        assertEquals("failure", result["status"])
        assertTrue(SecurityLoggingState.publisher!!.capturedMessages.isEmpty())
    }

    @Test
    fun ignoreLogErrorSwallowsAndFailureReasonIsClassName() {
        val error = IllegalStateException("customer ssn 123-45-6789")
        assertNull(ignoreLogError { throw error })
        assertEquals("IllegalStateException", extractFailureReason(error))
        assertEquals("unknown_error", extractFailureReason(null))
    }

    @Test
    fun lambdaContextDoesNotDecodeJwt() {
        initLambdaSecurityLogging(
            serviceName = "a-la-carte-pricing",
            envs = listOf(LambdaEnv(listOf("devmaj"), CloudEnvType.STAGE, "devmaj", "123456789012")),
            defaultAccountId = "unresolved",
            testMode = true,
        )
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDcSemACt8x4iTMCda8Yhe3iZaWbvV5XKSTbuAn0M"
        val context = buildLambdaContext(
            event = mapOf(
                "headers" to mapOf(
                    "authorization" to "Bearer $jwt",
                    "x-forwarded-for" to "203.0.113.195, 70.41.3.18",
                    "user-agent" to "JestClient/1.0",
                ),
            ),
            identity = mapOf("token" to jwt),
        )
        assertEquals("unknown", context["session_id"])
        assertEquals("unknown", context["actor_identifier"])
        assertEquals("203.0.113.195", context["source_ip_address"])
        assertEquals("JestClient/1.0", context["user_agent"])
        val pre = buildLambdaContext(event = emptyMap(), preSession = true)
        assertEquals("pre_session", pre["session_id"])
    }

    @Test
    fun retriablePrimaryFailurePublishesToFailover() {
        resetSecurityLoggingForTests()
        val regions = mutableListOf<String>()
        initSecurityLogging(
            testMode = false,
            enableFailover = true,
            topicArn = DEFAULT_TOPIC_ARN,
            failoverTopicArn = DEFAULT_FAILOVER_TOPIC_ARN,
            cloudEnvType = CloudEnvType.STAGE,
            cloudEnvUniqueId = "123456789012",
            cloudEnvName = "devmaj",
            serviceAccountId = "role",
            serviceName = "a-la-carte-pricing",
            transport = SnsTransport { _, region, _ ->
                regions.add(region)
                if (region == "us-west-2") throw RetriablePublishException("timeout")
            },
        )
        val result = logRecordAccess(
            env = env,
            endpointPath = "/allocations",
            dataSensitivityLevel = DataSensitivityLevel.CONFIDENTIAL,
            idList = emptyList(),
            detail = Detail.VIEW_RECORD,
            actorIdentifier = "unknown",
            actorType = ActorType.SYSTEM_SELF,
            sessionId = "unknown",
        )
        assertEquals("success", result["status"])
        assertEquals(listOf("us-west-2", "us-east-2"), regions)
        assertEquals(1, getFailoverMetrics()["failover_success"])
    }
}
