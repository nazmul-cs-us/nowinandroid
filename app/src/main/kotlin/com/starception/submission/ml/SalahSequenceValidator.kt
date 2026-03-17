package com.starception.submission.ml

import android.util.Log

/**
 * State machine that validates salah posture sequences to reduce false positives.
 *
 * Enforces:
 * 1. Valid posture transitions with relaxed rules for real-world ML detection
 * 2. Minimum duration per posture (prevents rapid flipping)
 * 3. Prayer confirmation after detecting any standing + prostration pattern
 * 4. Rak'ah counting for complete prayer tracking
 *
 * One rak'ah sequence (ideal):
 *   QIYAM → RUKU → QIYAM_RISING → GOING_TO_SUJUD → SUJUD → JALSA → SUJUD
 *
 * Last rak'ah ends with:
 *   ... → SUJUD → TASHAHHUD
 *
 * In practice, the ML model may skip intermediate postures or detect them
 * out of strict order, so transitions are kept permissive.
 */
class SalahSequenceValidator {

    companion object {
        private const val TAG = "SalahSequenceValidator"

        // Minimum time (ms) a posture must be held before transitioning
        // Reduced from 800ms - with ~200ms inference rate, 400ms = 2 stable readings
        private const val MIN_POSTURE_DURATION_MS = 400L

        // Minimum consecutive detections at same posture to confirm it
        // Reduced from 3 to 2 for faster response
        private const val MIN_STABLE_COUNT = 2

        // Prayer timeout: auto-complete if no posture change for 10 minutes
        private const val PRAYER_TIMEOUT_MS = 10 * 60 * 1000L

        // High-confidence override threshold (lowered from 0.9 to 0.8, restricted to adjacent transitions)
        private const val HIGH_CONFIDENCE_OVERRIDE = 0.80f

        // Valid transitions: tightened to enforce real prayer order.
        // Removed invalid shortcuts (e.g., QIYAM directly to SUJUD).
        // QIYAM_RISING is treated as equivalent to QIYAM for transition purposes.
        private val VALID_TRANSITIONS: Map<SalahPosture, Set<SalahPosture>> = mapOf(
            SalahPosture.QIYAM to setOf(
                SalahPosture.RUKU,
                SalahPosture.QIYAM_RISING     // Model may alternate between QIYAM/QIYAM_RISING
            ),
            SalahPosture.RUKU to setOf(
                SalahPosture.QIYAM_RISING,
                SalahPosture.GOING_TO_SUJUD   // May go directly to sujud from ruku
            ),
            SalahPosture.QIYAM_RISING to setOf(
                SalahPosture.QIYAM,
                SalahPosture.GOING_TO_SUJUD,
                SalahPosture.RUKU
            ),
            SalahPosture.GOING_TO_SUJUD to setOf(
                SalahPosture.SUJUD,
                SalahPosture.QIYAM_RISING     // Rising back up (aborted sujud)
            ),
            SalahPosture.SUJUD to setOf(
                SalahPosture.JALSA,
                SalahPosture.TASHAHHUD,
                SalahPosture.QIYAM_RISING     // Rising for next rak'ah
            ),
            SalahPosture.JALSA to setOf(
                SalahPosture.SUJUD,           // Second sujud
                SalahPosture.QIYAM_RISING,    // Rising for next rak'ah
                SalahPosture.TASHAHHUD         // Model may confuse JALSA/TASHAHHUD
            ),
            SalahPosture.TASHAHHUD to setOf(
                SalahPosture.QIYAM,           // Next rak'ah
                SalahPosture.QIYAM_RISING     // Rising for next rak'ah
            )
        )

        // Adjacent transitions (one step away in prayer flow) - used for high-confidence override
        // These are transitions that skip exactly one step and are plausible in real prayers
        private val ADJACENT_OVERRIDES: Map<SalahPosture, Set<SalahPosture>> = mapOf(
            SalahPosture.QIYAM to setOf(SalahPosture.GOING_TO_SUJUD),      // Skipped RUKU detection
            SalahPosture.RUKU to setOf(SalahPosture.SUJUD),                 // Skipped GOING_TO_SUJUD
            SalahPosture.SUJUD to setOf(SalahPosture.QIYAM),               // Skipped JALSA/QIYAM_RISING
            SalahPosture.JALSA to setOf(SalahPosture.GOING_TO_SUJUD),      // Going to second sujud
            SalahPosture.GOING_TO_SUJUD to setOf(SalahPosture.JALSA)       // Brief transition
        )
    }

    /** Current prayer session state */
    enum class PrayerState {
        IDLE,           // Not detecting prayer
        DETECTING,      // Potential prayer started (first posture seen)
        CONFIRMED,      // Prayer confirmed (valid rak'ah pattern detected)
        COMPLETED       // Prayer completed (salaam detected via TASHAHHUD end)
    }

    /** Result from processing a new posture detection */
    data class ValidationResult(
        val accepted: Boolean,
        val confirmedPosture: SalahPosture?,
        val prayerState: PrayerState,
        val rakahCount: Int,
        val message: String
    )

    // Current state
    private var currentPosture: SalahPosture? = null
    private var currentPostureStartTime: Long = 0L
    private var stableCount: Int = 0
    private var lastRawPosture: SalahPosture? = null

    // Prayer tracking
    private var prayerState: PrayerState = PrayerState.IDLE
    private var rakahCount: Int = 0
    private var sujudCountInRakah: Int = 0
    private var postureHistory: MutableList<SalahPosture> = mutableListOf()

    // Rak'ah milestone tracking
    private var seenRukuInRakah = false
    private var seenSujudInRakah = false
    private var seenStandingInSession = false

    // Prayer timeout tracking
    private var lastPostureChangeTime: Long = 0L

    /**
     * Process a new posture detection from the ML model.
     *
     * @param detectedPosture The posture detected by the CNN model
     * @param confidence The softmax confidence (0-1)
     * @param timestampMs Current time in milliseconds
     * @return ValidationResult indicating whether the detection was accepted
     */
    fun processDetection(
        detectedPosture: SalahPosture,
        confidence: Float,
        timestampMs: Long
    ): ValidationResult {

        // Prayer timeout: auto-complete if no posture change for 10 minutes
        if (prayerState == PrayerState.CONFIRMED && lastPostureChangeTime > 0L) {
            val timeSinceLastChange = timestampMs - lastPostureChangeTime
            if (timeSinceLastChange > PRAYER_TIMEOUT_MS) {
                Log.d(TAG, "Prayer timeout: no posture change for ${timeSinceLastChange / 1000}s")
                return completePrayer()
            }
        }

        // Count consecutive same-posture detections for stability
        if (detectedPosture == lastRawPosture) {
            stableCount++
        } else {
            stableCount = 1
            lastRawPosture = detectedPosture
        }

        // Require minimum stable detections before accepting
        if (stableCount < MIN_STABLE_COUNT) {
            return ValidationResult(
                accepted = false,
                confirmedPosture = currentPosture,
                prayerState = prayerState,
                rakahCount = rakahCount,
                message = "Stabilizing: $detectedPosture ($stableCount/$MIN_STABLE_COUNT)"
            )
        }

        // Same posture as current - just sustaining
        if (detectedPosture == currentPosture) {
            return ValidationResult(
                accepted = true,
                confirmedPosture = currentPosture,
                prayerState = prayerState,
                rakahCount = rakahCount,
                message = "Sustaining: ${detectedPosture.displayName}"
            )
        }

        // Treat QIYAM_RISING as equivalent to QIYAM for "same posture" check
        if (isStandingEquivalent(detectedPosture) && isStandingEquivalent(currentPosture)) {
            // Model alternating between QIYAM and QIYAM_RISING - treat as sustaining
            currentPosture = detectedPosture
            return ValidationResult(
                accepted = true,
                confirmedPosture = currentPosture,
                prayerState = prayerState,
                rakahCount = rakahCount,
                message = "Sustaining (standing): ${detectedPosture.displayName}"
            )
        }

        // First posture detection (prayer may be starting)
        if (currentPosture == null) {
            return handleFirstPosture(detectedPosture, timestampMs)
        }

        // Check minimum duration for current posture
        val elapsed = timestampMs - currentPostureStartTime
        if (elapsed < MIN_POSTURE_DURATION_MS) {
            return ValidationResult(
                accepted = false,
                confirmedPosture = currentPosture,
                prayerState = prayerState,
                rakahCount = rakahCount,
                message = "Too fast: ${currentPosture?.displayName} held ${elapsed}ms (min ${MIN_POSTURE_DURATION_MS}ms)"
            )
        }

        // Check if transition is valid
        val allowedNext = VALID_TRANSITIONS[currentPosture] ?: emptySet()
        if (detectedPosture !in allowedNext) {
            Log.d(TAG, "Invalid transition: ${currentPosture?.displayName} → ${detectedPosture.displayName}")
            // If we're in IDLE/DETECTING, reset and try as new start
            if (prayerState == PrayerState.IDLE || prayerState == PrayerState.DETECTING) {
                reset()
                return handleFirstPosture(detectedPosture, timestampMs)
            }
            // If prayer is confirmed, allow high-confidence override ONLY for adjacent transitions
            // (skip-one-step transitions that are plausible in real prayers)
            if (confidence >= HIGH_CONFIDENCE_OVERRIDE) {
                val adjacentAllowed = ADJACENT_OVERRIDES[currentPosture] ?: emptySet()
                if (detectedPosture in adjacentAllowed) {
                    Log.d(TAG, "Adjacent override: ${currentPosture?.displayName} → ${detectedPosture.displayName} (conf=$confidence)")
                    return acceptTransition(detectedPosture, timestampMs)
                }
            }
            return ValidationResult(
                accepted = false,
                confirmedPosture = currentPosture,
                prayerState = prayerState,
                rakahCount = rakahCount,
                message = "Invalid transition: ${currentPosture?.displayName} → ${detectedPosture.displayName}"
            )
        }

        // Valid transition - accept it
        return acceptTransition(detectedPosture, timestampMs)
    }

    private fun isStandingEquivalent(posture: SalahPosture?): Boolean {
        return posture == SalahPosture.QIYAM || posture == SalahPosture.QIYAM_RISING
    }

    private fun handleFirstPosture(posture: SalahPosture, timestampMs: Long): ValidationResult {
        // Prayer can start with QIYAM or QIYAM_RISING (both represent standing)
        if (posture == SalahPosture.QIYAM || posture == SalahPosture.QIYAM_RISING) {
            currentPosture = posture
            currentPostureStartTime = timestampMs
            lastPostureChangeTime = timestampMs
            prayerState = PrayerState.DETECTING
            postureHistory.add(posture)
            seenStandingInSession = true
            Log.d(TAG, "Prayer detection started: ${posture.displayName}")
            return ValidationResult(
                accepted = true,
                confirmedPosture = posture,
                prayerState = prayerState,
                rakahCount = rakahCount,
                message = "Prayer may be starting: ${posture.displayName}"
            )
        }

        // Non-standing first detection - not a prayer start
        return ValidationResult(
            accepted = false,
            confirmedPosture = null,
            prayerState = PrayerState.IDLE,
            rakahCount = 0,
            message = "Waiting for Qiyam/QiyamRising to start prayer detection"
        )
    }

    private fun acceptTransition(newPosture: SalahPosture, timestampMs: Long): ValidationResult {
        val oldPosture = currentPosture
        currentPosture = newPosture
        currentPostureStartTime = timestampMs
        lastPostureChangeTime = timestampMs
        postureHistory.add(newPosture)

        Log.d(TAG, "Transition: ${oldPosture?.displayName} → ${newPosture.displayName}")

        // Track standing
        if (isStandingEquivalent(newPosture)) {
            seenStandingInSession = true
        }

        // Track rak'ah milestones
        when (newPosture) {
            SalahPosture.RUKU -> {
                seenRukuInRakah = true
                // Confirm prayer once we see standing + bowing (strong prayer signal)
                if (prayerState == PrayerState.DETECTING && seenStandingInSession) {
                    prayerState = PrayerState.CONFIRMED
                    Log.d(TAG, "Prayer CONFIRMED: standing + ruku detected")
                }
            }
            SalahPosture.GOING_TO_SUJUD -> {
                // Also a strong prayer signal (going to prostration)
                if (prayerState == PrayerState.DETECTING && seenStandingInSession) {
                    prayerState = PrayerState.CONFIRMED
                    Log.d(TAG, "Prayer CONFIRMED: standing + going to sujud detected")
                }
            }
            SalahPosture.SUJUD -> {
                seenSujudInRakah = true
                sujudCountInRakah++

                // Confirm prayer after seeing any prostration if we saw standing
                if (prayerState == PrayerState.DETECTING && seenStandingInSession) {
                    prayerState = PrayerState.CONFIRMED
                    Log.d(TAG, "Prayer CONFIRMED: standing + sujud detected")
                }
            }
            SalahPosture.QIYAM, SalahPosture.QIYAM_RISING -> {
                // Rising to standing from sitting postures means new rak'ah
                if (oldPosture == SalahPosture.JALSA || oldPosture == SalahPosture.TASHAHHUD) {
                    if (sujudCountInRakah >= 2) {
                        rakahCount++
                        Log.d(TAG, "Rak'ah $rakahCount completed")
                    } else if (sujudCountInRakah >= 1) {
                        // Count partial rak'ah too (model may miss one sujud)
                        rakahCount++
                        Log.d(TAG, "Rak'ah $rakahCount completed (partial: ${sujudCountInRakah} sujud detected)")
                    }
                    // Reset rak'ah tracking
                    sujudCountInRakah = 0
                    seenRukuInRakah = false
                    seenSujudInRakah = false
                }
                // Also count rak'ah if rising from sujud directly (skipping JALSA)
                else if (oldPosture == SalahPosture.SUJUD) {
                    if (sujudCountInRakah >= 1) {
                        rakahCount++
                        Log.d(TAG, "Rak'ah $rakahCount completed (from sujud)")
                    }
                    sujudCountInRakah = 0
                    seenRukuInRakah = false
                    seenSujudInRakah = false
                }
            }
            SalahPosture.TASHAHHUD -> {
                // Tashahhud might be final sitting - count the rak'ah
                if (seenSujudInRakah && sujudCountInRakah >= 1) {
                    rakahCount++
                    Log.d(TAG, "Rak'ah $rakahCount completed (entering Tashahhud)")
                    sujudCountInRakah = 0
                    seenRukuInRakah = false
                    seenSujudInRakah = false
                }
                // Confirm prayer if we see tashahhud after standing
                if (prayerState == PrayerState.DETECTING && seenStandingInSession) {
                    prayerState = PrayerState.CONFIRMED
                    Log.d(TAG, "Prayer CONFIRMED: standing + tashahhud detected")
                }
            }
            else -> { /* No special tracking */ }
        }

        return ValidationResult(
            accepted = true,
            confirmedPosture = newPosture,
            prayerState = prayerState,
            rakahCount = rakahCount,
            message = "${oldPosture?.displayName} → ${newPosture.displayName}"
        )
    }

    /**
     * Mark prayer as completed (called externally when salaam/end is detected).
     */
    fun completePrayer(): ValidationResult {
        if (prayerState == PrayerState.CONFIRMED) {
            prayerState = PrayerState.COMPLETED
            Log.d(TAG, "Prayer completed: $rakahCount rak'ahs")
        }
        val result = ValidationResult(
            accepted = true,
            confirmedPosture = currentPosture,
            prayerState = prayerState,
            rakahCount = rakahCount,
            message = "Prayer completed: $rakahCount rak'ahs"
        )
        return result
    }

    /**
     * Get a summary of the current prayer session.
     */
    fun getSessionSummary(): String {
        return buildString {
            append("State: $prayerState")
            append(", Rak'ahs: $rakahCount")
            append(", Current: ${currentPosture?.displayName ?: "none"}")
            append(", History: ${postureHistory.joinToString("→") { it.displayName }}")
        }
    }

    /**
     * Reset all state for a new prayer detection session.
     */
    fun reset() {
        currentPosture = null
        currentPostureStartTime = 0L
        lastPostureChangeTime = 0L
        stableCount = 0
        lastRawPosture = null
        prayerState = PrayerState.IDLE
        rakahCount = 0
        sujudCountInRakah = 0
        postureHistory.clear()
        seenRukuInRakah = false
        seenSujudInRakah = false
        seenStandingInSession = false
        Log.d(TAG, "Reset")
    }

    /** Whether a prayer has been confirmed (valid rak'ah pattern detected) */
    val isPrayerConfirmed: Boolean get() = prayerState == PrayerState.CONFIRMED

    /** Whether prayer is completed */
    val isPrayerCompleted: Boolean get() = prayerState == PrayerState.COMPLETED

    /** Current confirmed posture (null if none) */
    val currentConfirmedPosture: SalahPosture? get() = currentPosture

    /** Number of completed rak'ahs */
    val completedRakahs: Int get() = rakahCount
}
