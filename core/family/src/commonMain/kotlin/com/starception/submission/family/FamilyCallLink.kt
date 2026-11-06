package com.starception.submission.family

/** Only a canonical call ID crosses the public URL boundary. No answer flag or credentials. */
class FamilyCallLink {
    fun callId(input: String): String? = Pattern.matchEntire(input)?.groupValues?.get(1)?.lowercase()
    private companion object {
        val Pattern = Regex("deenly://family-call\\?callId=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})")
    }
}
