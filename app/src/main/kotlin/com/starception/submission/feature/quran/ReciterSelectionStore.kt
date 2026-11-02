package com.starception.submission.feature.quran

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class ReciterSelection(
    val id: String? = null,
    val name: String = DEFAULT_RECITER_NAME,
    val riwayah: String = "",
)

const val DEFAULT_RECITER_NAME = "Mishary Rashid Al-Alafasy"

/** Observe edits made by any picker, including an already-open reader or Settings page. */
class ReciterSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE)

    fun current(): ReciterSelection {
        val id = preferences.getString("selected_id", null) ?: return ReciterSelection()
        val legacyName = preferences.getString("selected_name", null).orEmpty()
        return ReciterSelection(
            id = id,
            name = legacyName.substringBefore(" · ").takeIf { it.isNotBlank() && it != "Deenly audio" }
                ?: DEFAULT_RECITER_NAME,
            riwayah = preferences.getString("selected_riwayah", null)
                ?: legacyName.substringAfter(" · ", ""),
        )
    }

    val selections: Flow<ReciterSelection> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("selected_")) trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(current())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun select(reciter: ReciterCatalog.Reciter?) {
        preferences.edit().apply {
            if (reciter == null) {
                remove("selected_id")
                remove("selected_server")
                remove("selected_surahs")
                remove("selected_riwayah")
                putString("selected_name", DEFAULT_RECITER_NAME)
            } else {
                putString("selected_id", reciter.id)
                putString("selected_name", reciter.name)
                putString("selected_riwayah", reciter.riwaya)
                putString("selected_server", reciter.server)
                putString("selected_surahs", reciter.surahs.joinToString(","))
            }
        }.apply()
    }
}
