package com.starception.submission.download

import android.app.Application
import android.content.ContextWrapper
import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.ml.DeenlyKnowledgeTask
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ModelAvailabilityRefreshTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun newlyAssembledModelIsDetectedWithoutWaitingForNegativeCacheExpiry() {
        val directory = temporary.newFolder("files")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir() = directory
        }
        val task = DeenlyKnowledgeTask.QUESTION
        val file = File(directory, "cdn_assets/models/now_nudge/${DeenlyKnowledgeModel.MODEL_FILE_NAME}")
        assertFalse(DeenlyKnowledgeModel.isModelDownloaded(context, task, forceRefresh = true))
        file.parentFile!!.mkdirs()
        try {
            // Sparse test fixture: no model inference or gigabyte allocation required.
            RandomAccessFile(file, "rw").use { it.setLength(DeenlyKnowledgeModel.MODEL_SIZE_BYTES) }
            assertTrue(DeenlyKnowledgeModel.isModelDownloaded(context, task))
            assertFalse(DeenlyKnowledgeModel.isModelDownloaded(context, DeenlyKnowledgeTask.KNOWLEDGE, forceRefresh = true))
        } finally {
            file.delete()
            DeenlyKnowledgeTask.entries.forEach { DeenlyKnowledgeModel.isModelDownloaded(context, it, forceRefresh = true) }
        }
    }
}
