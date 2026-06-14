package com.starception.submission.usersettings

import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.HighLatitudeAdjustment
import com.starception.submission.prayer.model.PrayerCalculationSettings
import com.starception.submission.prayer.model.PrayerTimeOffsets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical per-country store for prayer settings, backed by [UserSettingsDatabase].
 *
 * The repository keeps SharedPreferences as the fast active-country cache; this store is the
 * source of record for each country's tuning and is what gets uploaded to Cloudflare. Any user
 * edit calls [markLocalChange] so the (dormant) sync layer can push the DB file.
 */
@Singleton
class UserSettingsStore @Inject constructor(
    private val dao: UserSettingsDao,
) {
    private val _localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits whenever a user-driven settings change is persisted (debounced + consumed by sync). */
    val localChanges: SharedFlow<Unit> = _localChanges.asSharedFlow()

    suspend fun getForCountry(countryCode: String): PrayerCalculationSettings? =
        dao.getCountry(countryCode.normalizeCountry())?.toCalculationSettings()

    suspend fun hasCountry(countryCode: String): Boolean =
        dao.getCountry(countryCode.normalizeCountry()) != null

    /**
     * Persist [settings] for [countryCode]. When [autoDetectedCountryName] is null the previously
     * stored display name (if any) is preserved.
     */
    suspend fun save(
        countryCode: String,
        settings: PrayerCalculationSettings,
        autoDetectedCountryName: String? = null,
    ) {
        val code = countryCode.normalizeCountry()
        val name = autoDetectedCountryName ?: dao.getCountry(code)?.autoDetectedCountryName
        dao.upsertCountry(settings.toEntity(code, name))
    }

    suspend fun lastKnownCountry(): String? = dao.getMeta(KEY_LAST_KNOWN_COUNTRY)

    suspend fun setLastKnownCountry(countryCode: String) =
        dao.putMeta(SettingsMetaEntity(KEY_LAST_KNOWN_COUNTRY, countryCode.normalizeCountry()))

    suspend fun getMeta(key: String): String? = dao.getMeta(key)

    suspend fun putMeta(key: String, value: String?) =
        dao.putMeta(SettingsMetaEntity(key, value))

    fun markLocalChange() {
        _localChanges.tryEmit(Unit)
    }

    companion object {
        const val KEY_LAST_KNOWN_COUNTRY = "last_known_country_code"
        // Country the user explicitly chose to NOT switch to ("Keep current"); suppresses re-prompts
        // for that country only. Cleared when they return to the active country.
        const val KEY_DECLINED_COUNTRY = "declined_country_code"
        // Global (not per-country) notification preferences, JSON-encoded, synced to the cloud.
        const val KEY_NOTIFICATION_PREFS_JSON = "notification_preferences_json"
        const val KEY_LAST_LOCAL_CHANGE_AT = "last_local_change_at"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
        const val KEY_SYNCED_USER_ID = "synced_user_id"
    }
}

private fun String.normalizeCountry(): String = trim().uppercase()

private fun PrayerCountrySettingsEntity.toCalculationSettings(): PrayerCalculationSettings =
    PrayerCalculationSettings(
        calculationMethod = parseEnum(calculationMethod, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        asrMadhhab = parseEnum(asrMadhhab, AsrMadhhab.STANDARD),
        highLatitudeAdjustment = parseEnum(highLatitudeAdjustment, HighLatitudeAdjustment.NONE),
        customFajrAngle = customFajrAngle,
        customIshaAngle = customIshaAngle,
        customIshaDelay = customIshaDelay,
        customMaghribOffset = customMaghribOffset,
        timeOffsets = PrayerTimeOffsets(
            fajr = offsetFajr,
            sunrise = offsetSunrise,
            dhuhr = offsetDhuhr,
            asr = offsetAsr,
            maghrib = offsetMaghrib,
            isha = offsetIsha,
        ),
    )

private fun PrayerCalculationSettings.toEntity(
    countryCode: String,
    autoDetectedCountryName: String?,
): PrayerCountrySettingsEntity =
    PrayerCountrySettingsEntity(
        countryCode = countryCode,
        calculationMethod = calculationMethod.name,
        asrMadhhab = asrMadhhab.name,
        highLatitudeAdjustment = highLatitudeAdjustment.name,
        customFajrAngle = customFajrAngle,
        customIshaAngle = customIshaAngle,
        customIshaDelay = customIshaDelay,
        customMaghribOffset = customMaghribOffset,
        offsetFajr = timeOffsets.fajr,
        offsetSunrise = timeOffsets.sunrise,
        offsetDhuhr = timeOffsets.dhuhr,
        offsetAsr = timeOffsets.asr,
        offsetMaghrib = timeOffsets.maghrib,
        offsetIsha = timeOffsets.isha,
        autoDetectedCountryName = autoDetectedCountryName,
        updatedAt = System.currentTimeMillis(),
    )

private inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)
