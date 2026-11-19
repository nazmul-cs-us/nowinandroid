package com.starception.submission.download

import com.starception.submission.feature.ai.modelVersion
import com.starception.submission.ml.DeenlyKnowledgeModel
import kotlinx.serialization.json.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ModelVersionTest {
    @Test fun currentCatalogMatchesTheVersionsRequiredByTheRuntime() {
        val manifest = Json.parseToJsonElement(File("src/main/assets/manifest.json").readText()).jsonObject
        val assets = manifest.getValue("assets").jsonObject
        for ((category, filename) in listOf(
            "model_now_nudge" to DeenlyKnowledgeModel.MODEL_FILE_NAME,
            "model_now_nudge_knowledge" to DeenlyKnowledgeModel.ANSWER_MODEL_FILE_NAME,
        )) {
            val keys = assets.filterValues { it.jsonObject.getValue("category").jsonPrimitive.content == category }.keys
            assertNotNull(modelVersion(category, keys))
            assertEquals(modelVersion(category, listOf(filename)), modelVersion(category, keys))
        }
    }
    @Test fun multipartDownloadsHaveOneVersionAndDoNotUseQuantizationAsTheVersion() {
        assertEquals("v5", modelVersion("model_now_nudge",
            (0..3).map { "models/now_nudge/deenly-question-v5-q4_k_m.gguf.part-0$it" }))
        assertEquals("v1.2", modelVersion("model_reader", listOf("models/reader-v1.2.onnx")))
    }
    @Test fun missingOrAmbiguousReleaseMetadataDoesNotInventAnInstalledVersion() {
        assertNull(modelVersion("model_now_nudge", emptyList()))
        assertNull(modelVersion("model_now_nudge", listOf("models/reader.gguf")))
        assertNull(modelVersion("model_reader", listOf("reader-v1.gguf", "reader-v2.gguf")))
        assertNull(modelVersion("quran_core", listOf("quran-v1.db")))
    }
}
