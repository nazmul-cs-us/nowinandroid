package com.starception.submission.shared.audio

import com.starception.submission.core.assets.AssetPlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** MP3Quran catalog shared with the Android reciter list, fetched on iOS. */
object IosReciterCatalog {
    suspend fun fetch(): List<QuranReciterSource> {
        val body = AssetPlatform.fetchText("https://www.mp3quran.net/api/v3/reciters?language=eng")
            ?: return emptyList()
        val root = Json.parseToJsonElement(body).jsonObject
        return root["reciters"]?.jsonArray.orEmpty().flatMap { item ->
            val reciter = item.jsonObject
            val reciterId = reciter["id"]?.jsonPrimitive?.content ?: return@flatMap emptyList()
            val name = reciter["name"]?.jsonPrimitive?.content ?: return@flatMap emptyList()
            reciter["moshaf"]?.jsonArray.orEmpty().mapNotNull { variant ->
                val moshaf = variant.jsonObject
                val variantId = moshaf["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val server = moshaf["server"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!server.startsWith("https://")) return@mapNotNull null
                val surahs = moshaf["surah_list"]?.jsonPrimitive?.content.orEmpty()
                    .split(',').mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..114 }.toSet()
                if (surahs.isEmpty()) return@mapNotNull null
                QuranReciterSource(
                    id = "$reciterId-$variantId",
                    displayName = if (reciterId == "54") "Abdul Rahman Al-Sudais" else name,
                    baseUrl = server,
                    availableSurahs = surahs,
                    riwaya = moshaf["name"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }.distinctBy { it.id }.sortedBy { it.displayName }
    }
}
