package com.sunrun.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

private val json = ObjectMapper()

private val COMMON_REQUIRED = listOf(
    "cloud_env_type",
    "cloud_env_unique_id",
    "cloud_env_name",
    "service_account_id",
    "service_name",
)

private val validValues: Map<String, Set<String>> = mapOf(
    "status" to setOf(Status.SUCCESS, Status.FAILURE),
    "actor_type" to setOf(
        ActorType.HUMAN_INTERNAL, ActorType.HUMAN_PARTNER, ActorType.HUMAN_CUSTOMER, ActorType.HUMAN_EXTERNAL,
        ActorType.SERVICE_INTERNAL, ActorType.SERVICE_PARTNER, ActorType.SERVICE_CUSTOMER, ActorType.SYSTEM_SELF,
    ),
    "event_type" to setOf(
        EventType.LOGIN_ATTEMPT, EventType.USER_LOGOUT, EventType.MFA_CHALLENGE, EventType.PERMISSION_CHANGE,
        EventType.USER_STATUS_CHANGE, EventType.IMPERSONATION_EVENT, EventType.USER_INVITE_EVENT,
        EventType.API_REQUEST_PROCESSED, EventType.RECORD_ACCESS, EventType.MFA_STATUS_CHANGE,
        EventType.PASSWORD_CHANGE_RESET, EventType.API_KEY_LIFECYCLE, EventType.AUTH_MECHANISM_MODIFICATION,
    ),
    "auth_protocol" to setOf(
        AuthProtocol.BASIC_AUTH, AuthProtocol.FORM_BASED, AuthProtocol.API_KEY, AuthProtocol.M2M_TOKEN,
        AuthProtocol.SESSION_COOKIE, AuthProtocol.OAUTH2_JWT, AuthProtocol.OAUTH2_CLIENT_CREDENTIALS,
        AuthProtocol.OAUTH2_AUTHORIZATION_CODE, AuthProtocol.OAUTH2_IMPLICIT, AuthProtocol.OAUTH2_PASSWORD_GRANT,
        AuthProtocol.SAML, AuthProtocol.OIDC, AuthProtocol.NONE,
    ),
    "detail" to setOf(
        Detail.INVALID_CREDENTIALS, Detail.ACCOUNT_LOCKED, Detail.IP_RESTRICTED, Detail.MFA_REQUIRED,
        Detail.POLICY_VIOLATION, Detail.CAPTCHA_FAILURE, Detail.TOKEN_EXPIRED, Detail.TOKEN_INVALID,
        Detail.UNAUTHORIZED_ACCESS, Detail.RATE_LIMIT_EXCEEDED, Detail.USER_INITIATED, Detail.ADMIN_INITIATED,
        Detail.SYSTEM_AUTOMATED, Detail.SYSTEM_POLICY_VIOLATION, Detail.SESSION_TIMEOUT, Detail.CONCURRENT_SESSION,
        Detail.FAILED_ATTEMPTS_THRESHOLD, Detail.INTERNAL_ERROR, Detail.SERVICE_UNAVAILABLE, Detail.MAINTENANCE,
        Detail.INVALID_REQUEST, Detail.NOT_APPLICABLE, Detail.API_KEY_CREATED, Detail.API_KEY_REVOKED,
        Detail.API_KEY_PERMISSIONS_MODIFIED, Detail.SSO_CONFIG_CREATED, Detail.SSO_CONFIG_MODIFIED,
        Detail.SSO_CONFIG_DELETED, Detail.LOCAL_AUTH_ENABLED, Detail.LOCAL_AUTH_DISABLED, Detail.USER_DISABLED,
        Detail.USER_ENABLED, Detail.USER_DELETED, Detail.USER_LOCKED, Detail.USER_UNLOCKED,
        Detail.IMPERSONATION_START, Detail.IMPERSONATION_STOP, Detail.VIEW_LIST, Detail.MODIFY_CUSTOMER_DATA,
        Detail.EXPORT_REPORT, Detail.VIEW_RECORD, Detail.EDIT_RECORD, Detail.CREATE_RECORD, Detail.DELETE_RECORD,
        Detail.MFA_DISABLED, Detail.MFA_ENABLED, Detail.NEW_MFA_DEVICE, Detail.MFA_INVALID_CODE,
        Detail.MFA_EXPIRED_CODE, Detail.MFA_DEVICE_NOT_ENROLLED, Detail.MFA_TOO_MANY_ATTEMPTS,
        Detail.PASSWORD_CHANGE, Detail.PASSWORD_RESET, Detail.NEW_SSO_PROVIDER, Detail.ENABLE_LOCAL_AUTHN,
        Detail.DISABLE_SSO,
    ),
    "mfa_type" to setOf(
        MfaType.SMS, MfaType.TOTP, MfaType.PUSH, MfaType.EMAIL, MfaType.OKTA_VERIFY, MfaType.AUTHENTICATOR_APP,
        MfaType.HARDWARE_TOKEN, MfaType.BIOMETRIC, MfaType.BACKUP_CODES,
    ),
    "http_method" to setOf(
        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS,
    ),
    "data_sensitivity_level" to setOf(
        DataSensitivityLevel.PUBLIC, DataSensitivityLevel.NON_PUBLIC, DataSensitivityLevel.CONFIDENTIAL, DataSensitivityLevel.RESTRICTED,
    ),
    "endpoint_sensitivity" to setOf(
        EndpointSensitivity.PUBLIC, EndpointSensitivity.NON_PUBLIC, EndpointSensitivity.CONFIDENTIAL, EndpointSensitivity.RESTRICTED,
    ),
    "user_role" to setOf(
        UserRole.ADMIN, UserRole.SALES_REP, UserRole.CUSTOMER_SUPPORT, UserRole.PARTNER_ADMIN,
        UserRole.CUSTOMER_USER, UserRole.NON_ADMIN_INTERNAL, UserRole.NOT_AVAILABLE,
    ),
    "invite_status" to setOf(InviteStatus.SENT, InviteStatus.ACCEPTED, InviteStatus.REVOKED, InviteStatus.EXPIRED),
    "cloud_env_type" to setOf(CloudEnvType.PROD, CloudEnvType.STAGE, CloudEnvType.TEST, CloudEnvType.DEV),
)

internal object SecurityLoggingState {
    var publisher: SnsPublisher? = null
    var envConfig: MutableMap<String, String> = mutableMapOf()
    var errorHandler: ((String, String?) -> Unit)? = null

    fun reset() {
        publisher = null
        envConfig = mutableMapOf()
        errorHandler = null
    }
}

const val DEFAULT_TOPIC_ARN = "arn:aws:sns:us-west-2:000576341507:sr-sec-logging-log-topic-prod"
const val DEFAULT_FAILOVER_TOPIC_ARN = "arn:aws:sns:us-east-2:000576341507:sr-sec-logging-log-topic-failover-prod"

fun getEnvironmentConfig(): Map<String, String> = SecurityLoggingState.envConfig.toMap()

fun initSecurityLogging(
    topicArn: String? = null,
    regionName: String? = null,
    failoverTopicArn: String? = null,
    failoverRegion: String? = null,
    enableFailover: Boolean = true,
    testMode: Boolean = false,
    cloudEnvType: String? = null,
    cloudEnvUniqueId: String? = null,
    cloudEnvName: String? = null,
    serviceAccountId: String? = null,
    serviceName: String? = null,
    logToLocalStdout: Boolean = false,
    localLogging: Boolean = false,
    transport: SnsTransport? = null,
) {
    val env = mutableMapOf<String, String>()
    cloudEnvType?.let { env["cloud_env_type"] = it }
    cloudEnvUniqueId?.let { env["cloud_env_unique_id"] = it }
    cloudEnvName?.let { env["cloud_env_name"] = it }
    serviceAccountId?.let { env["service_account_id"] = it }
    serviceName?.let { env["service_name"] = it }
    SecurityLoggingState.envConfig = env

    val topic = topicArn ?: System.getenv("SECURITY_LOGS_TOPIC_ARN") ?: if (testMode) {
        "arn:aws:sns:us-east-1:123456789012:test-security-logs"
    } else {
        DEFAULT_TOPIC_ARN
    }
    val region = regionName ?: regionFromArn(topic) ?: System.getenv("AWS_REGION") ?: "us-west-2"
    val failoverTopic = when {
        failoverTopicArn != null -> failoverTopicArn
        enableFailover && !testMode -> System.getenv("SECURITY_LOGS_FAILOVER_TOPIC_ARN") ?: DEFAULT_FAILOVER_TOPIC_ARN
        else -> null
    }
    val failoverReg = failoverRegion ?: failoverTopic?.let { regionFromArn(it) } ?: System.getenv("SECURITY_LOGS_FAILOVER_REGION") ?: "us-east-2"
    SecurityLoggingState.publisher = SnsPublisher(
        topicArn = topic,
        regionName = region,
        failoverTopicArn = failoverTopic,
        failoverRegion = failoverReg,
        enableFailover = enableFailover,
        testMode = testMode,
        transport = transport ?: AwsSnsTransport(),
        logToLocalStdout = logToLocalStdout || localLogging,
        mapper = json,
    )
}

fun resetSecurityLoggingForTests() {
    SecurityLoggingState.reset()
}

fun setSecurityLoggingErrorHandler(handler: (String, String?) -> Unit) {
    SecurityLoggingState.errorHandler = handler
}

fun getFailoverMetrics(): Map<String, Int> = publisher().getMetrics()

private fun publisher(): SnsPublisher =
    SecurityLoggingState.publisher ?: throw IllegalStateException("Security logging not initialized. Call initSecurityLogging() first.")

private fun regionFromArn(arn: String): String? = Regex("^arn:aws:sns:([^:]+):").find(arn)?.groupValues?.get(1)

private fun missing(params: Map<String, Any?>, fields: List<String>): List<String> =
    fields.filter { field ->
        val value = params[field]
        value == null || (value is String && value.isBlank())
    }

private fun standardizedError(field: String, value: String, allowCustom: Boolean): String? {
    val allowed = validValues[field] ?: return null
    if (value in allowed) return null
    if (allowCustom && field == "detail" && value.isNotBlank()) return null
    return "Invalid value for $field: $value"
}

private fun validate(params: Map<String, Any?>, required: List<String>, standardized: List<Pair<String, Boolean>>): List<String> {
    val errors = mutableListOf<String>()
    val missingFields = missing(params, required)
    if (missingFields.isNotEmpty()) {
        errors.add("Required fields missing: ${missingFields.joinToString(", ")}")
    }
    for ((field, allowCustom) in standardized) {
        val value = params[field] as? String ?: continue
        if (value.isBlank()) continue
        standardizedError(field, value, allowCustom)?.let { errors.add(it) }
    }
    return errors
}

private fun callerContext(): Pair<String, String> {
    val frame = Thread.currentThread().stackTrace.firstOrNull { frame ->
        val name = frame.className
        !name.startsWith("com.sunrun.security") && !name.startsWith("java.") && !name.startsWith("kotlin.") && !name.startsWith("jdk.")
    }
    return (frame?.methodName ?: "") to (frame?.fileName ?: "")
}

private fun baseEvent(
    eventType: String,
    logCategory: String,
    status: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    cloudEnvType: String,
    cloudEnvUniqueId: String,
    cloudEnvName: String,
    serviceAccountId: String,
    serviceName: String,
    extras: Map<String, Any?>,
): MutableMap<String, Any?> {
    val env = SecurityLoggingState.envConfig
    val (callerFunction, callerFile) = callerContext()
    val event = linkedMapOf<String, Any?>(
        "timestamp" to (extras["timestamp"] as? String).orEmpty().ifBlank { formatSecurityTimestamp() },
        "event_type" to eventType,
        "log_category" to logCategory,
        "status" to status,
        "actor_identifier" to actorIdentifier,
        "actor_type" to actorType,
        "session_id" to sessionId,
        "cloud_env_type" to cloudEnvType.ifBlank { env["cloud_env_type"].orEmpty() },
        "service_name" to serviceName.ifBlank { env["service_name"].orEmpty() },
        "cloud_env_unique_id" to cloudEnvUniqueId.ifBlank { env["cloud_env_unique_id"].orEmpty() },
        "cloud_env_name" to cloudEnvName.ifBlank { env["cloud_env_name"].orEmpty() },
        "service_account_id" to serviceAccountId.ifBlank { env["service_account_id"].orEmpty() },
        "source_ip_address" to (extras["source_ip_address"] as? String).orEmpty(),
        "cloud_service_api_type" to (extras["cloud_service_api_type"] as? String).orEmpty(),
        "caller_function" to callerFunction,
        "caller_file" to callerFile,
    )
    listOf("service_component_name", "trace_id", "span_id", "parent_span_id", "user_role_context").forEach { key ->
        val value = extras[key] as? String
        if (!value.isNullOrBlank()) event[key] = value
    }
    val context = extras["context"] as? Map<*, *>
    if (!context.isNullOrEmpty()) event["context"] = context
    extras.forEach { (key, value) ->
        if (key !in event && value != null && value != "") event[key] = value
    }
    return event
}

private fun publishOrFail(name: String, errors: List<String>, event: MutableMap<String, Any?>): Map<String, String> {
    if (errors.isNotEmpty()) {
        return mapOf("status" to "failure", "message" to "Validation errors for $name:\n- ${errors.joinToString("\n- ")}")
    }
    return try {
        publisher().publishMessage(event)
    } catch (ex: Exception) {
        val message = "Error in $name: ${ex.javaClass.simpleName}"
        SecurityLoggingState.errorHandler?.invoke(message, event["event_type"] as? String)
        mapOf("status" to "failure", "message" to message)
    }
}

data class EnvFields(
    val cloudEnvType: String,
    val cloudEnvUniqueId: String,
    val cloudEnvName: String,
    val serviceAccountId: String,
    val serviceName: String,
)

fun logUserLogin(
    env: EnvFields,
    eventType: String,
    userAgent: String,
    userRole: String,
    status: String,
    authProtocol: String,
    actorIdentifier: String = "",
    actorType: String = "",
    sessionId: String = "",
    sourceIpAddress: String = "",
    detail: String = "",
    deviceId: String = "",
): Map<String, String> {
    val params = mapOf(
        "cloud_env_type" to env.cloudEnvType,
        "cloud_env_unique_id" to env.cloudEnvUniqueId,
        "cloud_env_name" to env.cloudEnvName,
        "service_account_id" to env.serviceAccountId,
        "service_name" to env.serviceName,
        "event_type" to eventType,
        "user_agent" to userAgent,
        "user_role" to userRole,
        "status" to status,
        "auth_protocol" to authProtocol,
        "actor_type" to actorType,
        "detail" to detail,
    )
    val errors = validate(
        params,
        COMMON_REQUIRED + listOf("event_type", "status", "user_agent", "user_role", "auth_protocol"),
        listOf("event_type" to false, "status" to false, "user_role" to false, "auth_protocol" to false, "actor_type" to false, "detail" to true),
    )
    val event = baseEvent(eventType, LogCategory.AUTHN_SESSION, status, actorIdentifier, actorType, sessionId, env.cloudEnvType, env.cloudEnvUniqueId, env.cloudEnvName, env.serviceAccountId, env.serviceName, mapOf("user_agent" to userAgent, "user_role" to userRole, "auth_protocol" to authProtocol, "detail" to detail, "device_id" to deviceId, "source_ip_address" to sourceIpAddress))
    return publishOrFail("user login", errors, event)
}

fun logMfaChallenge(
    env: EnvFields,
    eventType: String,
    userAgent: String,
    userRole: String,
    status: String,
    mfaType: String,
    actorIdentifier: String = "",
    actorType: String = "",
    sessionId: String = "",
    sourceIpAddress: String = "",
    detail: String = "",
): Map<String, String> = authnEvent("mfa challenge", LogCategory.AUTHN_SESSION, env, eventType, status, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("user_agent" to userAgent, "user_role" to userRole, "mfa_type" to mfaType, "detail" to detail), COMMON_REQUIRED + listOf("event_type", "status", "user_agent", "user_role", "mfa_type"), listOf("event_type" to false, "status" to false, "user_role" to false, "mfa_type" to false, "actor_type" to false, "detail" to true))

fun logUserLogout(
    env: EnvFields,
    eventType: String,
    userAgent: String,
    userRole: String,
    status: String,
    actorIdentifier: String = "",
    actorType: String = "",
    sessionId: String = "",
    sourceIpAddress: String = "",
    detail: String = "",
): Map<String, String> = authnEvent("user logout", LogCategory.AUTHN_SESSION, env, eventType, status, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("user_agent" to userAgent, "user_role" to userRole, "detail" to detail), COMMON_REQUIRED + listOf("event_type", "status", "user_agent", "user_role"), listOf("event_type" to false, "status" to false, "user_role" to false, "actor_type" to false, "detail" to true))

/**
 * Python v2.3.0 hardcodes status to SUCCESS. [status] defaults to that.
 * Pass [Status.FAILURE] when the handler failed. record_count is set by the publisher from [idList].
 */
fun logRecordAccess(
    env: EnvFields,
    eventType: String = EventType.RECORD_ACCESS,
    endpointPath: String,
    dataSensitivityLevel: String,
    idList: List<String>,
    detail: String,
    status: String = Status.SUCCESS,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    sourceIpAddress: String = "",
    fieldsAccessed: List<String>? = null,
    context: Map<String, Any?>? = null,
    userAgent: String = "",
): Map<String, String> {
    val params = mutableMapOf<String, Any?>(
        "cloud_env_type" to env.cloudEnvType,
        "cloud_env_unique_id" to env.cloudEnvUniqueId,
        "cloud_env_name" to env.cloudEnvName,
        "service_account_id" to env.serviceAccountId,
        "service_name" to env.serviceName,
        "event_type" to eventType,
        "endpoint_path" to endpointPath,
        "data_sensitivity_level" to dataSensitivityLevel,
        "id_list" to idList,
        "detail" to detail,
        "actor_identifier" to actorIdentifier,
        "actor_type" to actorType,
        "session_id" to sessionId,
        "status" to status,
    )
    val errors = validate(
        params,
        COMMON_REQUIRED + listOf("event_type", "actor_identifier", "actor_type", "session_id", "endpoint_path", "data_sensitivity_level", "id_list", "detail", "status"),
        listOf("event_type" to false, "actor_type" to false, "data_sensitivity_level" to false, "detail" to false, "status" to false),
    )
    val extras = mutableMapOf<String, Any?>(
        "endpoint_path" to endpointPath,
        "data_sensitivity_level" to dataSensitivityLevel,
        "id_list" to idList,
        "detail" to detail,
        "source_ip_address" to sourceIpAddress,
        "user_agent" to userAgent,
        "context" to context,
    )
    if (fieldsAccessed != null) extras["fields_accessed"] = fieldsAccessed
    val event = baseEvent(eventType, LogCategory.CUSTOMER_DATA_ACTIONS, status, actorIdentifier, actorType, sessionId, env.cloudEnvType, env.cloudEnvUniqueId, env.cloudEnvName, env.serviceAccountId, env.serviceName, extras)
    return publishOrFail("record access", errors, event)
}

fun logPermissionRoleChange(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetUserIdentifier: String,
    objectChanged: String,
    previousValue: String,
    newValue: String,
    detail: String = "",
    sourceIpAddress: String = "",
): Map<String, String> = changeEvent("permission role change", LogCategory.AUTHZ_ACCESS, env, eventType, Status.SUCCESS, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("target_user_identifier" to targetUserIdentifier, "object_changed" to objectChanged, "previous_value" to previousValue, "new_value" to newValue, "detail" to detail), listOf("target_user_identifier", "object_changed", "previous_value", "new_value"), listOf("event_type" to false, "detail" to true))

fun logUserStatusChange(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetUserIdentifier: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = changeEvent("user status change", LogCategory.AUTHZ_ACCESS, env, eventType, Status.SUCCESS, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("target_user_identifier" to targetUserIdentifier, "detail" to detail), listOf("target_user_identifier", "detail"), listOf("event_type" to false, "actor_type" to false, "detail" to false))

fun logImpersonationEvent(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetUserIdentifier: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = changeEvent("impersonation event", LogCategory.AUTHZ_ACCESS, env, eventType, Status.SUCCESS, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("target_user_identifier" to targetUserIdentifier, "detail" to detail), listOf("target_user_identifier", "detail"), listOf("event_type" to false, "actor_type" to false, "detail" to true))

fun logUserInviteEvent(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetUserEmail: String,
    assignedRole: String,
    inviteStatus: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = changeEvent("user invite event", LogCategory.AUTHZ_ACCESS, env, eventType, Status.SUCCESS, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("target_user_email" to targetUserEmail, "assigned_role" to assignedRole, "invite_status" to inviteStatus, "detail" to detail), listOf("target_user_email", "assigned_role", "invite_status", "detail"), listOf("event_type" to false, "actor_type" to false, "invite_status" to false, "detail" to true))

fun logApiRequest(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    authProtocol: String,
    endpointPath: String,
    httpMethod: String,
    authorizationStatus: String,
    endpointSensitivity: String,
    sourceIpAddress: String = "",
): Map<String, String> = changeEvent("api request", LogCategory.API_ENDPOINT_ACCESS, env, eventType, Status.SUCCESS, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("auth_protocol" to authProtocol, "endpoint_path" to endpointPath, "http_method" to httpMethod, "authorization_status" to authorizationStatus, "endpoint_sensitivity" to endpointSensitivity), listOf("auth_protocol", "endpoint_path", "http_method", "authorization_status", "endpoint_sensitivity"), listOf("event_type" to false, "actor_type" to false, "auth_protocol" to false, "http_method" to false, "endpoint_sensitivity" to false))

fun logMfaStatusChange(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetObject: String,
    status: String,
    mfaId: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = changeEvent("mfa status change", LogCategory.KEY_CONFIG_CHANGES, env, eventType, status, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("target_object" to targetObject, "mfa_id" to mfaId, "detail" to detail), listOf("target_object", "status", "mfa_id", "detail"), listOf("event_type" to false, "actor_type" to false, "status" to false, "detail" to false))

fun logPasswordChangeReset(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetObject: String,
    status: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = keyConfig("password change reset", env, eventType, status, actorIdentifier, actorType, sessionId, targetObject, detail, sourceIpAddress)

fun logApiKeyLifecycle(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetObject: String,
    status: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = keyConfig("api key lifecycle", env, eventType, status, actorIdentifier, actorType, sessionId, targetObject, detail, sourceIpAddress)

fun logAuthMechanismModification(
    env: EnvFields,
    eventType: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetObject: String,
    status: String,
    detail: String,
    sourceIpAddress: String = "",
): Map<String, String> = keyConfig("auth mechanism modification", env, eventType, status, actorIdentifier, actorType, sessionId, targetObject, detail, sourceIpAddress)

private fun keyConfig(
    name: String,
    env: EnvFields,
    eventType: String,
    status: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    targetObject: String,
    detail: String,
    sourceIpAddress: String,
): Map<String, String> = changeEvent(name, LogCategory.KEY_CONFIG_CHANGES, env, eventType, status, actorIdentifier, actorType, sessionId, sourceIpAddress, mapOf("target_object" to targetObject, "detail" to detail), listOf("target_object", "status", "detail"), listOf("event_type" to false, "actor_type" to false, "status" to false, "detail" to false))

private fun authnEvent(
    name: String,
    category: String,
    env: EnvFields,
    eventType: String,
    status: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    sourceIpAddress: String,
    extras: Map<String, Any?>,
    required: List<String>,
    standardized: List<Pair<String, Boolean>>,
): Map<String, String> {
    val params = mutableMapOf<String, Any?>(
        "cloud_env_type" to env.cloudEnvType,
        "cloud_env_unique_id" to env.cloudEnvUniqueId,
        "cloud_env_name" to env.cloudEnvName,
        "service_account_id" to env.serviceAccountId,
        "service_name" to env.serviceName,
        "event_type" to eventType,
        "status" to status,
        "actor_type" to actorType,
    )
    params.putAll(extras)
    val errors = validate(params, required, standardized)
    val event = baseEvent(eventType, category, status, actorIdentifier, actorType, sessionId, env.cloudEnvType, env.cloudEnvUniqueId, env.cloudEnvName, env.serviceAccountId, env.serviceName, extras + ("source_ip_address" to sourceIpAddress))
    return publishOrFail(name, errors, event)
}

private fun changeEvent(
    name: String,
    category: String,
    env: EnvFields,
    eventType: String,
    status: String,
    actorIdentifier: String,
    actorType: String,
    sessionId: String,
    sourceIpAddress: String,
    extras: Map<String, Any?>,
    extraRequired: List<String>,
    standardized: List<Pair<String, Boolean>>,
): Map<String, String> {
    val params = mutableMapOf<String, Any?>(
        "cloud_env_type" to env.cloudEnvType,
        "cloud_env_unique_id" to env.cloudEnvUniqueId,
        "cloud_env_name" to env.cloudEnvName,
        "service_account_id" to env.serviceAccountId,
        "service_name" to env.serviceName,
        "event_type" to eventType,
        "status" to status,
        "actor_identifier" to actorIdentifier,
        "actor_type" to actorType,
        "session_id" to sessionId,
    )
    params.putAll(extras)
    val errors = validate(params, COMMON_REQUIRED + listOf("event_type", "actor_identifier", "actor_type", "session_id") + extraRequired, standardized)
    val event = baseEvent(eventType, category, status, actorIdentifier, actorType, sessionId, env.cloudEnvType, env.cloudEnvUniqueId, env.cloudEnvName, env.serviceAccountId, env.serviceName, extras + ("source_ip_address" to sourceIpAddress))
    return publishOrFail(name, errors, event)
}
