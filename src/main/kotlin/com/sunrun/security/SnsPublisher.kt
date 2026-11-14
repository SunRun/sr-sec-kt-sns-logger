package com.sunrun.security

import com.fasterxml.jackson.databind.ObjectMapper
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import java.time.Instant
import java.util.UUID

private const val SNS_SAFE_MESSAGE_SIZE = 250 * 1024

fun interface SnsTransport {
    fun publish(topicArn: String, region: String, message: String)
}

class RetriablePublishException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AwsSnsTransport : SnsTransport {
    private val clients = mutableMapOf<String, SnsClient>()

    override fun publish(topicArn: String, region: String, message: String) {
        val client = clients.getOrPut(region) { SnsClient.builder().region(software.amazon.awssdk.regions.Region.of(region)).build() }
        try {
            client.publish(PublishRequest.builder().topicArn(topicArn).message(message).build())
        } catch (ex: SdkClientException) {
            throw RetriablePublishException(ex.message ?: "sdk_client_error", ex)
        } catch (ex: AwsServiceException) {
            val code = ex.awsErrorDetails()?.errorCode() ?: ""
            if (code in setOf("Throttling", "ServiceUnavailable", "RequestTimeout", "TooManyRequestsException")) {
                throw RetriablePublishException(code, ex)
            }
            throw ex
        }
    }
}

class CircuitBreaker(private val threshold: Int = 5, private val timeoutMs: Long = 60_000) {
    private var failureCount = 0
    private var openedAt = 0L

    fun shouldAttempt(now: Long = System.currentTimeMillis()): Boolean {
        if (failureCount < threshold) return true
        return now - openedAt >= timeoutMs
    }

    fun recordSuccess() {
        failureCount = 0
    }

    fun recordFailure() {
        failureCount += 1
        openedAt = System.currentTimeMillis()
    }
}

class SnsPublisher(
    private val topicArn: String,
    private val regionName: String,
    private val failoverTopicArn: String?,
    private val failoverRegion: String?,
    private val enableFailover: Boolean,
    val testMode: Boolean,
    private val transport: SnsTransport,
    private val logToLocalStdout: Boolean,
    private val mapper: ObjectMapper,
) {
    private val primaryBreaker = CircuitBreaker()
    private val metrics = mutableMapOf(
        "primary_success" to 0,
        "primary_failure" to 0,
        "failover_attempts" to 0,
        "failover_success" to 0,
        "failover_failure" to 0,
        "total_failures" to 0,
    )
    val capturedMessages = mutableListOf<Map<String, Any?>>()

    fun getMetrics(): Map<String, Int> = metrics.toMap()

    fun resetMetrics() {
        metrics.keys.forEach { metrics[it] = 0 }
    }

    fun publishMessage(logDetails: MutableMap<String, Any?>): Map<String, String> {
        val eventUuid = (logDetails["event_uuid"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        logDetails["event_uuid"] = eventUuid
        if ((logDetails["timestamp"] as? String).isNullOrBlank()) {
            logDetails["timestamp"] = formatSecurityTimestamp()
        }
        val idList = logDetails["id_list"] as? List<*>
        val messages = if (idList != null) {
            val raw = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(logDetails)
            if (raw.toByteArray(Charsets.UTF_8).size > SNS_SAFE_MESSAGE_SIZE) {
                batchIdList(logDetails, idList.map { it.toString() }, eventUuid)
            } else {
                logDetails["part_number"] = 1
                logDetails["total_parts"] = 1
                logDetails["record_count"] = idList.size
                logDetails["total_record_count"] = idList.size
                listOf(logDetails)
            }
        } else {
            listOf(logDetails)
        }

        var sawFailure = false
        var failureMessage = ""
        for (messageData in messages) {
            if (logToLocalStdout || localStdoutEnabled()) {
                println(mapper.writeValueAsString(messageData))
            }
            val body = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageData)
            if (testMode) {
                capturedMessages.add(messageData.toMap())
                continue
            }
            val primary = publishRegion(topicArn, regionName, body, primaryBreaker, primary = true)
            if (primary == null) {
                continue
            }
            if (!primary.retriable || !enableFailover || failoverTopicArn.isNullOrBlank() || failoverRegion.isNullOrBlank()) {
                sawFailure = true
                failureMessage = primary.error
                metrics["total_failures"] = metrics.getValue("total_failures") + 1
                continue
            }
            metrics["failover_attempts"] = metrics.getValue("failover_attempts") + 1
            val failover = publishRegion(failoverTopicArn, failoverRegion, body, CircuitBreaker(threshold = Int.MAX_VALUE), primary = false)
            if (failover == null) {
                metrics["failover_success"] = metrics.getValue("failover_success") + 1
            } else {
                sawFailure = true
                failureMessage = failover.error
                metrics["failover_failure"] = metrics.getValue("failover_failure") + 1
                metrics["total_failures"] = metrics.getValue("total_failures") + 1
            }
        }
        return if (sawFailure) mapOf("status" to "failure", "message" to failureMessage) else mapOf("status" to "success")
    }

    private fun publishRegion(
        arn: String,
        region: String,
        body: String,
        breaker: CircuitBreaker,
        primary: Boolean,
    ): PublishError? {
        if (!breaker.shouldAttempt()) {
            return PublishError("circuit_open", retriable = true)
        }
        return try {
            transport.publish(arn, region, body)
            breaker.recordSuccess()
            if (primary) metrics["primary_success"] = metrics.getValue("primary_success") + 1
            null
        } catch (ex: RetriablePublishException) {
            breaker.recordFailure()
            if (primary) metrics["primary_failure"] = metrics.getValue("primary_failure") + 1
            PublishError(ex.message ?: "retriable", retriable = true)
        } catch (ex: Exception) {
            breaker.recordFailure()
            if (primary) metrics["primary_failure"] = metrics.getValue("primary_failure") + 1
            PublishError(ex.javaClass.simpleName, retriable = false)
        }
    }

    private fun batchIdList(base: MutableMap<String, Any?>, idList: List<String>, eventUuid: String): List<MutableMap<String, Any?>> {
        val chunk = 50
        val batches = idList.chunked(chunk).map { ids ->
            val copy = base.toMutableMap()
            copy["id_list"] = ids
            copy["record_count"] = ids.size
            copy["event_uuid"] = eventUuid
            copy["total_record_count"] = idList.size
            copy
        }
        return batches.mapIndexed { index, batch ->
            batch["part_number"] = index + 1
            batch["total_parts"] = batches.size
            batch
        }
    }

    private fun localStdoutEnabled(): Boolean {
        val names = listOf("SR_SEC_LOG_TO_STDOUT", "SR_SEC_LOCAL_LOGGING", "SECURITY_LOGS_LOCAL_LOGGING")
        return names.any { name ->
            val value = System.getenv(name)?.lowercase()
            value == "true" || value == "1" || value == "yes"
        }
    }

    private data class PublishError(val error: String, val retriable: Boolean)
}
