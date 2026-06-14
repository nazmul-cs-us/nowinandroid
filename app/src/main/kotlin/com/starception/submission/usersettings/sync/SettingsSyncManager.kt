package com.starception.submission.usersettings.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.starception.submission.auth.AuthManager
import com.starception.submission.auth.AuthUiState
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.usersettings.PrayerCountrySettingsEntity
import com.starception.submission.usersettings.SettingsMetaEntity
import com.starception.submission.usersettings.UserSettingsDao
import com.starception.submission.usersettings.UserSettingsDatabase
import com.starception.submission.usersettings.UserSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the local user-settings SQLite DB to/from Cloudflare R2 per logged-in user.
 *
 * Storage is a private R2 bucket ([R2Config.BUCKET]) at `users/{uid}/settings.db`, where uid is the
 * Firebase user id — so each signed-in user gets their own "directory". R2 is reached directly via
 * the S3 API ([R2Client]); the account keys live in [R2Config] (see the security note there).
 *
 * - **Push:** on every persisted settings change ([UserSettingsStore.localChanges], debounced).
 * - **Pull:** on login / app start while logged in — downloaded rows are merged into the live DB.
 *
 * Sync only runs while signed in (no uid ⇒ no per-user directory ⇒ skip).
 */
@Singleton
class SettingsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val store: UserSettingsStore,
    private val dao: UserSettingsDao,
    private val repository: PrayerSettingsRepository,
    okHttpClient: OkHttpClient,
) {
    private val r2 = R2Client(okHttpClient)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return

        // Push: upload the DB file shortly after each settings change.
        scope.launch {
            store.localChanges
                .debounce(DEBOUNCE_MS)
                .collect { runCatching { upload() }.onFailure { Log.e(TAG, "upload failed", it) } }
        }

        // Pull: whenever the user becomes logged in (covers app-start-while-logged-in too).
        scope.launch {
            authManager.uiState
                .map { it is AuthUiState.LoggedIn }
                .distinctUntilChanged()
                .collect { loggedIn ->
                    if (loggedIn) runCatching { pull() }.onFailure { Log.e(TAG, "pull failed", it) }
                }
        }
    }

    private fun dbFile(): File = context.getDatabasePath(UserSettingsDatabase.DATABASE_NAME)

    /** Upload the local DB file to the signed-in user's cloud slot. */
    suspend fun upload() {
        val uid = authManager.currentUid() ?: return
        val file = dbFile()
        if (!file.exists() || file.length() == 0L) return

        if (r2.put(R2Config.settingsKey(uid), file.readBytes())) {
            store.putMeta(UserSettingsStore.KEY_LAST_SYNCED_AT, System.currentTimeMillis().toString())
            store.putMeta(UserSettingsStore.KEY_SYNCED_USER_ID, uid)
            Log.i(TAG, "✅ settings uploaded to users/$uid/settings.db (${file.length()} bytes)")
        }
    }

    /** Download the user's cloud DB (if any) and merge its rows into the live Room DB. */
    suspend fun pull() {
        val uid = authManager.currentUid() ?: return
        val bytes = r2.get(R2Config.settingsKey(uid)) ?: run {
            Log.i(TAG, "no remote settings for users/$uid")
            return
        }
        val tmp = File(context.cacheDir, "remote_user_settings.db")
        tmp.outputStream().use { it.write(bytes) }
        try {
            mergeFromRemote(tmp)
            repository.applyNotificationPrefsFromStore() // restore global notification prefs onto live settings
            Log.i(TAG, "✅ remote settings merged for users/$uid")
        } finally {
            tmp.delete()
        }
    }

    /**
     * Read rows from a downloaded SQLite file and upsert them into the live Room DB (remote wins).
     * Mirrors the read-rows-via-DAO approach used by DuaDatabase.refreshFromAssets — avoids closing
     * the live Room connection or hot-swapping the file. `last_known_country_code` is intentionally
     * NOT overwritten so a freshly-restored device keeps tracking its own current location.
     */
    private suspend fun mergeFromRemote(file: File) {
        val remote = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            remote.rawQuery("SELECT * FROM prayer_country_settings", null).use { c ->
                while (c.moveToNext()) {
                    dao.upsertCountry(
                        PrayerCountrySettingsEntity(
                            countryCode = c.getString(c.getColumnIndexOrThrow("countryCode")),
                            calculationMethod = c.getString(c.getColumnIndexOrThrow("calculationMethod")),
                            asrMadhhab = c.getString(c.getColumnIndexOrThrow("asrMadhhab")),
                            highLatitudeAdjustment = c.getString(c.getColumnIndexOrThrow("highLatitudeAdjustment")),
                            customFajrAngle = c.getDoubleOrNull("customFajrAngle"),
                            customIshaAngle = c.getDoubleOrNull("customIshaAngle"),
                            customIshaDelay = c.getIntOrNull("customIshaDelay"),
                            customMaghribOffset = c.getIntOrNull("customMaghribOffset"),
                            offsetFajr = c.getInt(c.getColumnIndexOrThrow("offsetFajr")),
                            offsetSunrise = c.getInt(c.getColumnIndexOrThrow("offsetSunrise")),
                            offsetDhuhr = c.getInt(c.getColumnIndexOrThrow("offsetDhuhr")),
                            offsetAsr = c.getInt(c.getColumnIndexOrThrow("offsetAsr")),
                            offsetMaghrib = c.getInt(c.getColumnIndexOrThrow("offsetMaghrib")),
                            offsetIsha = c.getInt(c.getColumnIndexOrThrow("offsetIsha")),
                            autoDetectedCountryName = c.getStringOrNull("autoDetectedCountryName"),
                            updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt")),
                        )
                    )
                }
            }
            remote.rawQuery("SELECT key, value FROM settings_meta", null).use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0)
                    // Device-local bookkeeping must not cross devices; the rest (notification prefs,
                    // declined country) is real synced state.
                    if (key in DEVICE_LOCAL_META_KEYS) continue
                    dao.putMeta(SettingsMetaEntity(key, if (c.isNull(1)) null else c.getString(1)))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "merge from remote failed", e)
        } finally {
            remote.close()
        }
    }

    companion object {
        private const val TAG = "SettingsSyncManager"
        private const val DEBOUNCE_MS = 2000L

        // Meta keys that are local to this device/session and must not be merged from a remote DB.
        private val DEVICE_LOCAL_META_KEYS = setOf(
            UserSettingsStore.KEY_LAST_KNOWN_COUNTRY,
            UserSettingsStore.KEY_LAST_SYNCED_AT,
            UserSettingsStore.KEY_SYNCED_USER_ID,
        )
    }
}

private fun android.database.Cursor.getDoubleOrNull(name: String): Double? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getDouble(i)
}

private fun android.database.Cursor.getIntOrNull(name: String): Int? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getInt(i)
}

private fun android.database.Cursor.getStringOrNull(name: String): String? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getString(i)
}
