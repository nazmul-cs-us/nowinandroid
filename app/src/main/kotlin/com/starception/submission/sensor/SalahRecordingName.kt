package com.starception.submission.sensor

import android.util.Log
import com.starception.submission.ml.SalahPosture
import org.json.JSONObject
import java.io.File

/**
 * Descriptive filenames for salah recordings.
 *
 * A recording's mode prefix alone does not say what is inside it: a complete guided
 * session and a four second single-posture take are both `salah_guided_<time>_<id>`.
 * That ambiguity is not cosmetic — a live recording once reached training wearing the
 * manual prefix and the model learned its own predictions. So a descriptor derived from
 * the file's actual contents is inserted after the mode prefix:
 *
 *     salah_guided_full_20260726_185023_c29e680b.jsonl
 *     salah_data_qiyam_20260312_234318_56b7cf1a.jsonl
 *     salah_reviewed_2rakah_20260729_065850_de1aab85.jsonl
 *
 * The descriptor goes *after* the prefix on purpose. Provenance gates in
 * [SalahDataCollectionService], [com.starception.submission.feature.salah.datacollection.PrayerReviewViewModel]
 * and the Python training loader all match with `startsWith`, so they keep working
 * untouched. Live recordings get no descriptor: their labels come from the model and
 * mean nothing until a human review rewrites them as `salah_reviewed_*`.
 */
object SalahRecordingName {

    private const val TAG = "SalahRecordingName"

    /** Every mode prefix, longest first so `salah_reviewed_` wins over any shorter match. */
    private val PREFIXES = listOf(
        SalahDataCollectionService.REVIEWED_FILE_PREFIX,
        SalahDataCollectionService.GUIDED_FILE_PREFIX,
        SalahDataCollectionService.LIVE_FILE_PREFIX,
        SalahDataCollectionService.MANUAL_FILE_PREFIX,
    ).sortedByDescending { it.length }

    private val POSTURE_SLUGS = mapOf(
        SalahPosture.QIYAM to "qiyam",
        SalahPosture.RUKU to "ruku",
        SalahPosture.GOING_TO_SUJUD to "going2sujud",
        SalahPosture.SUJUD to "sujud",
        SalahPosture.JALSA to "jalsa",
        SalahPosture.TASHAHHUD to "tashahhud",
        SalahPosture.QIYAM_RISING to "qiyamrising",
        SalahPosture.RISING_TO_QIYAM to "rising2qiyam",
        SalahPosture.NOT_PRAYING to "notpraying",
    )

    /** What a recording holds, read back from the file rather than from what was intended. */
    private data class Contents(
        val postures: Set<SalahPosture>,
        val targetRakahCount: Int?,
        val rowCount: Int,
    )

    /**
     * Descriptor for a recording, or null when the name should stay as it is.
     *
     * `full` means every prayer posture is present, which is what a complete guided run
     * produces. `partial6` means a run was stopped early or predates a class being added,
     * and is the honest label for a session that cannot stand alone in a split.
     *
     * Measured against [SalahPosture.prayerPostures], not the model's full label set: a
     * prayer recording should never be demoted to `partial8` for lacking the NOT_PRAYING
     * class, which by definition it cannot contain.
     */
    private fun descriptorFor(prefix: String, contents: Contents): String? {
        // Live labels are the model's own output. Describing them would dress up a guess
        // as ground truth, which is exactly how the bad training set was built.
        if (prefix == SalahDataCollectionService.LIVE_FILE_PREFIX) return null

        if (contents.rowCount == 0) return "empty"

        contents.postures.singleOrNull()?.let { return POSTURE_SLUGS[it] }

        if (prefix == SalahDataCollectionService.REVIEWED_FILE_PREFIX) {
            contents.targetRakahCount?.let { return "${it}rakah" }
        }

        val prayerPosturesPresent = contents.postures.count { it.isPrayerPosture }
        return if (prayerPosturesPresent == SalahPosture.prayerPostures.size) {
            "full"
        } else {
            "partial$prayerPosturesPresent"
        }
    }

    private fun readContents(file: File): Contents {
        val postures = mutableSetOf<SalahPosture>()
        var targetRakahCount: Int? = null
        var rowCount = 0
        try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    rowCount++
                    try {
                        val json = JSONObject(line)
                        runCatching { SalahPosture.valueOf(json.getString("posture")) }
                            .getOrNull()
                            ?.let { postures.add(it) }
                        if (targetRakahCount == null && json.has("target_rakah_count")) {
                            targetRakahCount = json.optInt("target_rakah_count")
                        }
                    } catch (_: Exception) {
                        // A malformed row still counts toward size; the training loader
                        // reports and skips it separately.
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read ${file.name}: ${e.message}")
        }
        return Contents(postures, targetRakahCount, rowCount)
    }

    /**
     * Split a filename into its mode prefix and the `<date>_<time>_<id>.jsonl` tail,
     * dropping any descriptor already present so renaming twice is a no-op.
     */
    private fun splitName(name: String): Pair<String, String>? {
        val prefix = PREFIXES.firstOrNull { name.startsWith(it) } ?: return null
        val rest = name.removePrefix(prefix)
        // A descriptor, when present, sits before the 8-digit date stamp.
        val tail = rest.substringAfter('_', missingDelimiterValue = "")
        return if (rest.take(8).all { it.isDigit() }) prefix to rest else prefix to tail
    }

    /** Descriptive name for [file], or its current name when nothing should change. */
    fun describedNameFor(file: File): String {
        val (prefix, tail) = splitName(file.name) ?: return file.name
        val descriptor = descriptorFor(prefix, readContents(file)) ?: return prefix + tail
        return "$prefix${descriptor}_$tail"
    }

    /**
     * Rename [file] to match its contents. Returns the file at its final path, which is
     * the original when the name is already correct or the target is taken.
     */
    fun renameToDescriptive(file: File): File {
        if (!file.exists()) return file
        val newName = describedNameFor(file)
        if (newName == file.name) return file

        val target = File(file.parentFile, newName)
        if (target.exists()) {
            Log.w(TAG, "Not renaming ${file.name}: $newName already exists")
            return file
        }
        return if (file.renameTo(target)) {
            Log.i(TAG, "Renamed ${file.name} -> $newName")
            target
        } else {
            Log.w(TAG, "Rename failed for ${file.name}")
            file
        }
    }
}
