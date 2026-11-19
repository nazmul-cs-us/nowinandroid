package com.starception.submission.feature.quran

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PublishedTafsirManifestTest {
    @Test fun everyEditionAndChapterMatchesItsAuditedSourceAndOwnOptionalCategory() {
        val bundle = Json.parseToJsonElement(File("src/main/assets/manifest.json").readText()).jsonObject
        val assets = bundle.getValue("assets").jsonObject
        val integrity = Json.parseToJsonElement(File("../docs/published-tafsirs-integrity.json").readText()).jsonObject
        val editions = integrity.getValue("editions").jsonArray
        assertEquals(10, editions.size)
        editions.forEach { item ->
            val source = item.jsonObject
            val edition = PublishedTafsirs.forBook(source.getValue("bookKey").jsonPrimitive.content)!!
            assertEquals(edition.bookKey, source.getValue("bookKey").jsonPrimitive.content)
            assertEquals(edition.sourceUrl, source.getValue("sourceUrl").jsonPrimitive.content)
            assertEquals(114, source.getValue("surah_count").jsonPrimitive.int)
            assertEquals(6236, source.getValue("ayah_count").jsonPrimitive.int)
            assertTrue(source.getValue("missing_ayahs").jsonArray.isEmpty())
            val files = source.getValue("files").jsonArray
            assertEquals((1..114).toSet(), files.map { it.jsonObject.getValue("surah").jsonPrimitive.int }.toSet())
            files.forEach { file ->
                val info = file.jsonObject
                val surah = info.getValue("surah").jsonPrimitive.int
                val entry = assets.getValue(edition.chapterKey(surah)).jsonObject
                assertEquals(info.getValue("sha256"), entry.getValue("sha256"))
                assertEquals(info.getValue("bytes"), entry.getValue("size"))
                assertEquals(edition.category, entry.getValue("category").jsonPrimitive.content)
                assertFalse(entry.getValue("required").jsonPrimitive.boolean)
                assertEquals(BengaliIbnKathir.ayahCounts[surah - 1], info.getValue("ayahs").jsonPrimitive.int)
            }
        }
        assertFalse(assets.keys.any { it.startsWith("tafsir/tr-ibn-kathir/") })
    }
}
