package com.starception.submission.feature.quran

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BengaliIbnKathirManifestTest {
    @Test fun everyReaderChapterIsRegisteredWithTheVerifiedSourceHashAndOptionalCategory() {
        val bundle = Json.parseToJsonElement(File("src/main/assets/manifest.json").readText()).jsonObject
        val assets = bundle.getValue("assets").jsonObject
        val corpus = Json.parseToJsonElement(File("../docs/bengali-ibn-kathir-manifest.json").readText()).jsonObject
        assertEquals(114, corpus.getValue("surah_count").jsonPrimitive.int)
        assertEquals(6236, corpus.getValue("ayah_count").jsonPrimitive.int)
        for (file in corpus.getValue("files").jsonArray) {
            val info = file.jsonObject
            val surah = info.getValue("surah").jsonPrimitive.int
            val actual = assets.getValue(BengaliIbnKathir.chapterKey(surah)).jsonObject
            assertEquals(info.getValue("sha256"), actual.getValue("sha256"))
            assertEquals(info.getValue("bytes"), actual.getValue("size"))
            assertEquals(BengaliIbnKathir.CATEGORY, actual.getValue("category").jsonPrimitive.content)
            assertFalse(actual.getValue("required").jsonPrimitive.boolean)
            assertEquals(BengaliIbnKathir.ayahCounts[surah - 1], info.getValue("ayahs").jsonPrimitive.int)
        }
        val categoryAssets = assets.values.filter { it.jsonObject.getValue("category").jsonPrimitive.content == BengaliIbnKathir.CATEGORY }
        val category = bundle.getValue("categories").jsonObject.getValue(BengaliIbnKathir.CATEGORY).jsonObject
        assertEquals(categoryAssets.size, category.getValue("file_count").jsonPrimitive.int)
        assertEquals(categoryAssets.sumOf { it.jsonObject.getValue("size").jsonPrimitive.long }, category.getValue("total_size").jsonPrimitive.long)
        assertEquals(assets.size, bundle.getValue("total_files").jsonPrimitive.int)
    }
}
