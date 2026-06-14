package com.starception.submission.usersettings.sync

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.security.MessageDigest
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal Cloudflare R2 client that signs requests with AWS Signature V4 (S3-compatible API).
 * Ports the signing in `scripts/s3_upload.py` to Kotlin. Object-level GET/PUT only.
 *
 * Calls are blocking (OkHttp `execute()`); invoke from a background coroutine.
 */
class R2Client(private val okHttpClient: OkHttpClient) {

    /** PUT bytes at [key] (e.g. "users/{uid}/settings.db"). Returns true on 2xx. */
    fun put(key: String, body: ByteArray): Boolean {
        val request = signedRequest("PUT", key, body)
        return okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) Log.w(TAG, "PUT $key -> HTTP ${resp.code}")
            resp.isSuccessful
        }
    }

    /** GET bytes at [key]; null if absent (404) or on error. */
    fun get(key: String): ByteArray? {
        val request = signedRequest("GET", key, ByteArray(0))
        return okHttpClient.newCall(request).execute().use { resp ->
            when {
                resp.code == 404 -> null
                resp.isSuccessful -> resp.body?.bytes()
                else -> {
                    Log.w(TAG, "GET $key -> HTTP ${resp.code}")
                    null
                }
            }
        }
    }

    private fun signedRequest(method: String, key: String, body: ByteArray): Request {
        val (amzDate, dateStamp) = timestamps()
        val payloadHash = sha256Hex(body)

        // Canonical URI: each path segment of "bucket/key" percent-encoded, joined by "/".
        val canonicalUri = "/" + "${R2Config.BUCKET}/$key".split("/")
            .joinToString("/") { encodeSegment(it) }

        val canonicalHeaders =
            "host:${R2Config.HOST}\n" +
                "x-amz-content-sha256:$payloadHash\n" +
                "x-amz-date:$amzDate\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest =
            "$method\n$canonicalUri\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val scope = "$dateStamp/${R2Config.REGION}/${R2Config.SERVICE}/aws4_request"
        val stringToSign =
            "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray())}"

        val signingKey = signatureKey(R2Config.SECRET_KEY, dateStamp)
        val signature = hmacSha256(signingKey, stringToSign).toHex()

        val authorization =
            "AWS4-HMAC-SHA256 Credential=${R2Config.ACCESS_KEY}/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        val builder = Request.Builder()
            .url("${R2Config.ENDPOINT}$canonicalUri")
            .header("Authorization", authorization)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)

        return when (method) {
            "PUT" -> builder.put(body.toRequestBody(OCTET_STREAM)).build()
            else -> builder.get().build()
        }
    }

    private fun timestamps(): Pair<String, String> {
        val tz = TimeZone.getTimeZone("UTC")
        val amz = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = tz }
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
        val now = Date()
        return amz.format(now) to day.format(now)
    }

    private fun signatureKey(secret: String, dateStamp: String): ByteArray {
        val kDate = hmacSha256("AWS4$secret".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, R2Config.REGION)
        val kService = hmacSha256(kRegion, R2Config.SERVICE)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Percent-encode a single path segment, leaving RFC 3986 unreserved chars intact. */
    private fun encodeSegment(segment: String): String {
        val sb = StringBuilder()
        for (b in segment.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c.toChar().isUnreserved()) sb.append(c.toChar()) else sb.append("%%%02X".format(c))
        }
        return sb.toString()
    }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '_' || this == '.' || this == '~'

    companion object {
        private const val TAG = "R2Client"
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
