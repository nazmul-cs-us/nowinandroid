package com.starception.submission.usersettings.sync

/**
 * Cloudflare R2 (S3-compatible) configuration for syncing the user-settings DB.
 *
 * ⚠️ SECURITY: these are ACCOUNT-LEVEL R2 keys (same ones already committed in
 * `scripts/s3_upload.py`). Shipping them in the APK means anyone who unpacks the app can
 * read/write/delete EVERY bucket in this account. This is a deliberate "for now" choice to reuse
 * the project's existing credential without a server. Before any real release: mint a scoped R2
 * API token limited to [BUCKET] only, move it out of source (BuildConfig/local.properties), and
 * rotate these keys. Isolated here so that swap is a one-file change.
 */
object R2Config {
    const val ACCOUNT_ID = "a535d591f409b4a31c39625dc1ffd6c7"
    const val ACCESS_KEY = "de52f276c7a3a4b19e2a0aba392ea85e"
    const val SECRET_KEY = "e4681d6b8b96e999ec7e4a6e96a548e7aac6418cf349c32364008a22f8c842e0"

    /** Private bucket for per-user data (created separately from the public assets bucket). */
    const val BUCKET = "starception-userdata"

    const val REGION = "auto"
    const val SERVICE = "s3"
    const val HOST = "$ACCOUNT_ID.r2.cloudflarestorage.com"
    const val ENDPOINT = "https://$HOST"

    /** Object key for a user's settings DB: a per-user "directory" in the bucket. */
    fun settingsKey(uid: String): String = "users/$uid/settings.db"
}
