package com.sunrun.security

import java.security.MessageDigest

fun extractSessionId(session: Map<String, Any?>?): String {
    if (session == null) return "unknown"
    val explicit = session["id"] as? String ?: session["session_id"] as? String
    if (!explicit.isNullOrBlank()) return explicit
    val jti = session["jti"] as? String
    if (!jti.isNullOrBlank()) return "jwt_${sha16(jti)}"
    val email = extractActorIdentifier(session)
    val expires = session["expires"] ?: session["exp"]
    if (!email.isNullOrBlank() && expires != null && expires.toString().isNotBlank()) {
        return "sess_${sha16("$email:$expires")}"
    }
    val token = session["session_token"] as? String ?: session["sessionToken"] as? String
    if (!token.isNullOrBlank()) return "sess_${sha16(token)}"
    return "unknown"
}

fun extractActorIdentifier(session: Map<String, Any?>?): String? {
    if (session == null) return null
    val user = session["user"] as? Map<*, *>
    val email = session["email"] as? String ?: user?.get("email") as? String
    return email?.takeIf { it.contains("@") }
}

fun extractSourceIp(event: Map<String, Any?>?): String? {
    if (event == null) return null
    val headers = event["headers"] as? Map<*, *>
    val forwarded = header(headers, "x-forwarded-for")
    if (!forwarded.isNullOrBlank()) return forwarded.split(",")[0].trim()
    val requestContext = event["requestContext"] as? Map<*, *>
    val identity = requestContext?.get("identity") as? Map<*, *>
    val sourceIp = identity?.get("sourceIp") as? String
    if (!sourceIp.isNullOrBlank()) return sourceIp
    return null
}

fun extractUserAgent(event: Map<String, Any?>?): String? {
    val headers = event?.get("headers") as? Map<*, *> ?: return null
    return header(headers, "user-agent")?.takeIf { it.isNotBlank() }
}

private fun header(headers: Map<*, *>?, name: String): String? {
    if (headers == null) return null
    val match = headers.entries.firstOrNull { it.key.toString().equals(name, ignoreCase = true) }
    return match?.value?.toString()
}

private fun sha16(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}
