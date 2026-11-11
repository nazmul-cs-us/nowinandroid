package com.starception.submission.family

/** Shared signup rules. Existing account names/passwords are unaffected. */
class FamilyRegistrationPolicy {
    fun username(input: String): String? {
        val value = input.trim().lowercase()
        return value.takeIf { Regex("[a-z0-9][a-z0-9._-]{2,31}").matches(it) &&
            it !in setOf("admin", "administrator", "root", "support", "postmaster", "android", "ios") }
    }

    fun validate(input: String, password: String, confirmation: String): String? = when {
        username(input) == null -> "invalid_username"
        password.length !in 8..128 || password.isBlank() || password.any { it.code < 32 || it.code == 127 } -> "invalid_password"
        password != confirmation -> "password_mismatch"
        else -> null
    }

    fun message(code: String): String = when (code) {
        "invalid_username", "bad-request", "jid-malformed" -> "Choose an available username with 3–32 letters, numbers, dots, underscores or hyphens. Start with a letter or number."
        "invalid_password", "not-acceptable" -> "Use a password with 8–128 characters, without line breaks or control characters."
        "password_mismatch" -> "The passwords do not match."
        "conflict" -> "That username is already taken. Choose another or sign in."
        "resource-constraint" -> "Too many signup attempts. Wait a minute and try again."
        "forbidden" -> "That username cannot be registered. Choose another."
        "not-allowed", "feature-not-implemented", "service-unavailable" -> "Sign-up is unavailable on the Family server. Please try again later."
        else -> "Could not create your account. Check your connection and try again."
    }
}
