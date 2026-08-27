package com.starception.submission.core.translation

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * Seeds the shared Quran/Hadith/Dua translation preference from the user's country.
 *
 * The preference is eligible only on a genuinely fresh install. Existing installs and
 * restored/manual selections are preserved, and a detected country is applied at most once.
 */
object LocationBasedTranslationDefaults {

    private const val TAG = "LocationTranslation"
    private const val PREFS_NAME = "quran_prefs"
    private const val TRANSLATION_KEY = "quran_translation"
    private const val STATE_KEY = "location_translation_default_state"
    private const val COUNTRY_KEY = "location_translation_default_country"

    private const val STATE_PENDING = "pending"
    private const val STATE_APPLIED = "applied"
    private const val STATE_USER_SELECTED = "user_selected"
    private const val STATE_EXISTING_INSTALL = "existing_install"

    /**
     * Records whether this installation is eligible before location detection begins.
     * Keeping a pending marker lets initialization survive later launches if location
     * permission or a country fix is not available during the first session.
     */
    fun prepareFirstInstall(context: Context) {
        val prefs = preferences(context)
        if (prefs.contains(STATE_KEY)) return

        val isFreshInstall = runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.firstInstallTime == packageInfo.lastUpdateTime
        }.getOrElse { error ->
            Log.w(TAG, "Could not determine install age; preserving existing defaults", error)
            false
        }

        prepare(prefs, isFreshInstall)
    }

    /**
     * Applies the detected country's supported language when this is still a pending first install.
     * Returns the selected language code when a value was written, otherwise null.
     */
    fun applyDetectedCountry(context: Context, countryCode: String?): String? =
        applyDetectedCountry(preferences(context), countryCode)

    internal fun prepare(prefs: SharedPreferences, isFreshInstall: Boolean) {
        if (prefs.contains(STATE_KEY)) return

        val state = when {
            // Auto Backup or an unusually early manual selection always wins.
            prefs.contains(TRANSLATION_KEY) -> STATE_USER_SELECTED
            isFreshInstall -> STATE_PENDING
            else -> STATE_EXISTING_INSTALL
        }
        prefs.edit().putString(STATE_KEY, state).apply()
    }

    internal fun applyDetectedCountry(
        prefs: SharedPreferences,
        countryCode: String?,
    ): String? {
        val normalizedCountry = countryCode?.trim()?.uppercase()
            ?.takeIf { it.length == 2 }
            ?: return null
        if (prefs.getString(STATE_KEY, null) != STATE_PENDING) return null

        // A user may choose a language while location detection is still running.
        if (prefs.contains(TRANSLATION_KEY)) {
            prefs.edit().putString(STATE_KEY, STATE_USER_SELECTED).apply()
            return null
        }

        val language = languageForCountry(normalizedCountry)
        prefs.edit()
            .putString(TRANSLATION_KEY, language)
            .putString(COUNTRY_KEY, normalizedCountry)
            .putString(STATE_KEY, STATE_APPLIED)
            .apply()
        Log.i(TAG, "Seeded first-install translation '$language' for $normalizedCountry")
        return language
    }

    /** Returns the closest translation shipped by the app for an ISO 3166-1 alpha-2 country. */
    fun languageForCountry(countryCode: String?): String {
        val country = countryCode?.trim()?.uppercase().orEmpty()
        return when (country) {
            in ARABIC_COUNTRIES -> "ar"
            "BD" -> "bn"
            in CHINESE_COUNTRIES -> "zh"
            in SPANISH_COUNTRIES -> "es"
            in FRENCH_COUNTRIES -> "fr"
            "ID" -> "id"
            in RUSSIAN_COUNTRIES -> "ru"
            "SE" -> "sv"
            "TR" -> "tr"
            "PK" -> "ur"
            else -> "en"
        }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val ARABIC_COUNTRIES = setOf(
        "AE", "BH", "DZ", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "MR",
        "OM", "PS", "QA", "SA", "SD", "SO", "SY", "TN", "YE",
    )
    private val CHINESE_COUNTRIES = setOf("CN", "HK", "MO", "TW")
    private val SPANISH_COUNTRIES = setOf(
        "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "ES", "GQ", "GT",
        "HN", "MX", "NI", "PA", "PE", "PR", "PY", "SV", "UY", "VE",
    )
    private val FRENCH_COUNTRIES = setOf(
        "BJ", "BF", "CD", "CF", "CG", "CI", "FR", "GA", "GN", "MC", "ML",
        "NE", "SN", "TG",
    )
    private val RUSSIAN_COUNTRIES = setOf("BY", "KZ", "KG", "RU")
}
