package com.sunrun.security

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SECURITY_LOG_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'").withZone(ZoneOffset.UTC)

/** Normalizer-compatible UTC timestamp (`validation.py` L246). */
fun formatSecurityTimestamp(instant: Instant = Instant.now()): String =
    SECURITY_LOG_TIMESTAMP_FORMATTER.format(instant)
