package com.sunrun.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the cold-start behaviour change: SNS clients are built during init rather
 * than on the first publish, so the cost lands in Lambda init instead of being
 * billed to the first request that logs.
 */
class ClientWarmupTest {
    @AfterEach
    fun tearDown() {
        resetSecurityLoggingForTests()
    }

    @Test
    fun initBuildsConfiguredClientsBeforeAnyPublish() {
        initSecurityLogging(
            topicArn = "arn:aws:sns:us-west-2:123456789012:primary",
            failoverTopicArn = "arn:aws:sns:us-east-2:123456789012:failover",
            testMode = false,
            cloudEnvType = CloudEnvType.DEV,
            cloudEnvUniqueId = "123456789012",
            cloudEnvName = "test",
            serviceAccountId = "arn:aws:lambda:us-west-2:123456789012:function:t",
            serviceName = "warmup-test",
        )

        val transport = initializedPublisherForTests().transport
        assertTrue(transport is AwsSnsTransport, "default transport should be AwsSnsTransport")
        val warmed = (transport as AwsSnsTransport).warmedRegions()
        assertTrue(warmed.contains("us-west-2"), "primary region should be warmed during init: $warmed")
        assertTrue(warmed.contains("us-east-2"), "failover region should be warmed during init: $warmed")
    }

    @Test
    fun failoverDisabledWarmsOnlyPrimary() {
        initSecurityLogging(
            topicArn = "arn:aws:sns:us-west-2:123456789012:primary",
            enableFailover = false,
            testMode = false,
            cloudEnvType = CloudEnvType.DEV,
            cloudEnvUniqueId = "123456789012",
            cloudEnvName = "test",
            serviceAccountId = "arn:aws:lambda:us-west-2:123456789012:function:t",
            serviceName = "warmup-test",
        )

        val warmed = (initializedPublisherForTests().transport as AwsSnsTransport).warmedRegions()
        assertEquals(listOf("us-west-2"), warmed)
    }

    @Test
    fun testModeDoesNotWarmClients() {
        initSecurityLogging(
            topicArn = "arn:aws:sns:us-west-2:123456789012:primary",
            testMode = true,
            cloudEnvType = CloudEnvType.DEV,
            cloudEnvUniqueId = "123456789012",
            cloudEnvName = "test",
            serviceAccountId = "arn:aws:lambda:us-west-2:123456789012:function:t",
            serviceName = "warmup-test",
        )

        val warmed = (initializedPublisherForTests().transport as AwsSnsTransport).warmedRegions()
        assertTrue(warmed.isEmpty(), "test mode never publishes, so no clients should be built: $warmed")
    }
}
