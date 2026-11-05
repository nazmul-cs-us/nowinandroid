package com.starception.submission.family

/** Public connection settings only. Passwords and TURN credentials never belong here. */
class FamilyEndpoint {
    val domain: String = "xmpp.starception.com"
    val port: Int = 5222
    val androidTestAccount: String = "android@$domain"
    val iosTestAccount: String = "ios@$domain"

    /** Accept a local username or a full account on our authenticated server. */
    fun account(input: String): String? {
        val value = input.trim()
        val normalized = FamilyPolicy().normalizeJid(if ('@' in value) value else "$value@$domain")
        return normalized?.takeIf { it.substringAfter('@') == domain }
    }

    fun testPeer(input: String): String = when (account(input)) {
        androidTestAccount -> iosTestAccount
        iosTestAccount -> androidTestAccount
        else -> ""
    }
}
