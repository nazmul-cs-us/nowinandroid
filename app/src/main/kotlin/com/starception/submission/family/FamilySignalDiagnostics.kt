package com.starception.submission.family

import org.json.JSONObject

/** Keep SDP, candidate addresses, tokens and arbitrary exception text out of logs. */
internal fun familySignalFailureDiagnostic(payload: JSONObject, error: Throwable): String {
    val type = payload.optString("type").takeIf { it in setOf("offer", "answer", "ice") } ?: "other"
    val code = error.message?.takeIf { it in setOf("not_active", "call_not_found", "not_family",
        "not_connected", "invalid_signal", "invalid_request", "unavailable") } ?: "none"
    val bytes = payload.toString().toByteArray(Charsets.UTF_8).size
    return "Signalling send failed: type=$type; bytes=$bytes; exception=${error.javaClass.simpleName}; serverCode=$code"
}
