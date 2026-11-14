package com.sunrun.security

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("security_logging")
private var lambdaInitialized = false
private val defaultEnvVars = listOf("ENV_NAME", "ENV", "envName")

/** Test-only hook to verify init guard does not crash cold start. */
internal var initLambdaSecurityLoggingTestFailure: (() -> Unit)? = null

data class LambdaEnv(
    val names: List<String>,
    val cloudEnvType: String,
    val cloudEnvName: String,
    val accountId: String? = null,
)

fun resetLambdaSecurityLogging() {
    lambdaInitialized = false
    initLambdaSecurityLoggingTestFailure = null
}

fun initLambdaSecurityLogging(
    serviceName: String,
    envs: List<LambdaEnv>,
    defaultAccountId: String,
    envVarNames: List<String> = defaultEnvVars,
    region: String = "us-west-2",
    testMode: Boolean = false,
    logToLocalStdout: Boolean = false,
    localLogging: Boolean = false,
    transport: SnsTransport? = null,
) {
    if (lambdaInitialized) return
    try {
        initLambdaSecurityLoggingTestFailure?.invoke()
        val rawName = envVarNames.firstNotNullOfOrNull { name -> System.getenv(name)?.takeIf { it.isNotBlank() } }.orEmpty()
        val match = envs.firstOrNull { rawName in it.names }
        val accountId = System.getenv("awsAccountId") ?: System.getenv("AWS_ACCOUNT_ID") ?: match?.accountId ?: defaultAccountId
        val functionName = System.getenv("AWS_LAMBDA_FUNCTION_NAME") ?: serviceName
        initSecurityLogging(
            regionName = region,
            testMode = testMode,
            cloudEnvType = match?.cloudEnvType ?: CloudEnvType.DEV,
            cloudEnvUniqueId = accountId,
            cloudEnvName = match?.cloudEnvName ?: rawName.ifBlank { "local" },
            serviceAccountId = "arn:aws:lambda:$region:$accountId:function:$functionName",
            serviceName = serviceName,
            logToLocalStdout = logToLocalStdout,
            localLogging = localLogging,
            transport = transport,
        )
        setSecurityLoggingErrorHandler { error, eventType ->
            logger.error("Failed to log {}: {}", eventType ?: "unknown", error)
        }
        lambdaInitialized = true
    } catch (t: Throwable) {
        System.err.println("[SecurityLogging] initLambdaSecurityLogging failed: ${t.message}")
    }
}

fun ignoreLogError(action: () -> Any?): Any? =
    try {
        action()
    } catch (ex: Exception) {
        null
    }

fun extractFailureReason(error: Throwable?): String {
    if (error == null) return "unknown_error"
    return error.javaClass.simpleName.ifBlank { "Error" }
}

fun actorFromResolvedIdentity(identity: Map<String, Any?>?): String {
    if (identity == null) return "unknown"
    emailFrom(identity["impersonator"] as? Map<*, *>, "email")?.let { return it }
    emailFrom((identity["demoUser"] ?: identity["demo_user"]) as? Map<*, *>, "email")?.let { return it }
    emailFrom(identity, "email", "sfdcEmail", "sfdc_email", "cognitoEmail", "cognito_email", "authenticatedEmail", "authenticated_email")?.let { return it }
    val cognito = (identity["cognitoUserData"] ?: identity["cognito_user_data"]) as? Map<*, *>
    return emailFrom(cognito, "email") ?: "unknown"
}

fun buildLambdaContext(
    event: Map<String, Any?>?,
    preSession: Boolean = false,
    sessionId: String? = null,
    identity: Map<String, Any?>? = null,
    actorIdentifier: String? = null,
    actorType: String? = null,
    userRole: String? = null,
): Map<String, String> {
    val resolvedSession = when {
        preSession -> "pre_session"
        !sessionId.isNullOrBlank() -> sessionId
        else -> sessionIdFromIdentity(identity)
    }
    val env = getEnvironmentConfig()
    return mapOf(
        "cloud_env_type" to env["cloud_env_type"].orEmpty(),
        "cloud_env_unique_id" to env["cloud_env_unique_id"].orEmpty(),
        "cloud_env_name" to env["cloud_env_name"].orEmpty(),
        "service_account_id" to env["service_account_id"].orEmpty(),
        "service_name" to env["service_name"].orEmpty(),
        "actor_identifier" to (actorIdentifier ?: if (identity != null) actorFromResolvedIdentity(identity) else "unknown"),
        "actor_type" to (actorType ?: ActorType.HUMAN_INTERNAL),
        "session_id" to resolvedSession,
        "source_ip_address" to (extractSourceIp(event) ?: "unknown"),
        "user_agent" to (extractUserAgent(event) ?: "unknown"),
        "user_role" to (userRole ?: UserRole.NOT_AVAILABLE),
    )
}

private fun sessionIdFromIdentity(identity: Map<String, Any?>?): String {
    if (identity == null) return "unknown"
    val user = identity["user"] as? Map<*, *>
    val email = emailFrom(identity, "email") ?: emailFrom(user, "email")
    val expires = identity["exp"] ?: user?.get("exp") ?: identity["expires"]
    if (email == null || expires == null || expires.toString().isBlank()) return "unknown"
    return extractSessionId(mapOf("user" to mapOf("email" to email), "expires" to expires.toString()))
}

private fun emailFrom(record: Map<*, *>?, vararg keys: String): String? {
    if (record == null) return null
    for (key in keys) {
        val value = record[key] as? String ?: continue
        if (value.contains("@") && value.length < 320 && !value.contains(" ")) return value
    }
    return null
}
