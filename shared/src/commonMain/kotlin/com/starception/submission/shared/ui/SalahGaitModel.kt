/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The walking-gait model from:
 *
 *   Kljuno & Williams II, "Humanoid Walking Robot: Modeling, Inverse
 *   Dynamics, and Gain Scheduling Control", Journal of Robotics 2010,
 *   doi:10.1155/2010/278597.
 *
 * The paper prescribes a sagittal-plane 10-DOF biped driven by recorded
 * human joint trajectories (its Figure 9: hip/knee/ankle flexion over the
 * gait cycle, 54.3 strides/min), a rolling foot contact so the transition
 * between cycles stays smooth (its Figure 7), and a mass-spring inverted
 * pendulum COM that breathes twice per stride (its Section 1.2).
 *
 * This port renders a walk-in-place cycle: the pelvis stays anchored with
 * the paper's COM bob, the stance foot is planted (its ankle rides the
 * heel-off lift of the rolling contact while the toe stays down), the
 * stance knee comes from the closed-form two-link inverse kinematics, and
 * the swing leg follows the Figure 9 curves with foot clearance.
 *
 * Forward is -z, up is +y — the same axes as [skeletonPoseFor].
 */
private const val STRIDES_PER_SECOND = 54.3f / 60f

/** Windows per second in a recorded salah session. */
internal const val SALAH_WINDOWS_PER_SECOND = 10f

/** Stride phase advanced by one recorded window (one window = 100 ms). */
internal val GAIT_PHASE_PER_WINDOW = STRIDES_PER_SECOND / SALAH_WINDOWS_PER_SECOND

private const val DEG = PI.toFloat() / 180f

// Skeleton segment lengths, measured from the standing keyframe.
private const val THIGH = 0.42f
private const val SHANK = 0.40f
private const val FOOT = 0.20f
private const val UPPER_ARM = 0.26f
private const val FOREARM = 0.26f

// Figure 9 control points: (stride phase, flexion in degrees).
// Positive hip flexion swings the thigh forward; positive knee flexion folds
// the shank backward; positive ankle flexion is dorsiflexion (toes up).
private val hipCurve = listOf(
    0.0f to 32f, 0.1f to 27f, 0.2f to 20f, 0.3f to 12f, 0.4f to 2f,
    0.5f to -8f, 0.6f to -4f, 0.7f to 14f, 0.8f to 24f, 0.9f to 30f, 1.0f to 32f,
)
private val kneeCurve = listOf(
    0.0f to 4f, 0.1f to 14f, 0.2f to 10f, 0.3f to 5f, 0.4f to 3f,
    0.5f to 6f, 0.6f to 38f, 0.7f to 58f, 0.8f to 34f, 0.9f to 12f, 1.0f to 4f,
)
private val ankleCurve = listOf(
    0.0f to 4f, 0.1f to 7f, 0.2f to 2f, 0.3f to 1f, 0.4f to 0f,
    0.5f to -12f, 0.6f to -22f, 0.7f to 2f, 0.8f to 8f, 0.9f to 4f, 1.0f to 4f,
)

/** Smooth (cosine-eased) interpolation between the curve's control points. */
private fun curveAt(curve: List<Pair<Float, Float>>, phase: Float): Float {
    val p = ((phase % 1f) + 1f) % 1f
    val upper = curve.indexOfFirst { it.first >= p }.let { if (it <= 0) curve.lastIndex else it }
    val (p0, v0) = curve[upper - 1]
    val (p1, v1) = curve[upper]
    val span = (p1 - p0).coerceAtLeast(0.0001f)
    val t = ((p - p0) / span).coerceIn(0f, 1f)
    val eased = (1f - cos(t * PI.toFloat())) * 0.5f
    return v0 + (v1 - v0) * eased
}

/** Sagittal point: up + forward (forward is -z in skeleton space). */
private data class Sagittal(val up: Float, val forward: Float)

/**
 * The full 16-joint skeleton mid-stride. [phase] in [0, 1) covers one stride
 * (two steps): phase 0 is the right heel strike, so the left leg trails by a
 * half cycle. Stance runs for the first 60% of each leg's cycle — the paper's
 * single-support phase — and swing completes it.
 */
internal fun walkingSkeletonPose(phase: Float): SkeletonJoints {
    val hipY = 0.905f + 0.018f * sin(4f * PI.toFloat() * phase)
    val lean = 0.06f + 0.015f * sin(2f * PI.toFloat() * phase)

    /**
     * One leg. While the foot is planted (its own phase < 0.6) the ankle is
     * pinned below the pelvis — the paper's rolling contact lifts the heel
     * toward toe-off while the toe stays down — and the knee comes from the
     * closed-form two-link IK. In swing the leg follows the Figure 9 curves
     * with the foot cleared above the ground.
     */
    fun leg(p: Float): Triple<Sagittal, Sagittal, Sagittal> {
        val hipAngle = curveAt(hipCurve, p) * DEG
        val kneeAngle = curveAt(kneeCurve, p) * DEG
        val ankleAngle = curveAt(ankleCurve, p) * DEG
        val stance = p < 0.6f

        val ankle: Sagittal
        val knee: Sagittal
        if (stance) {
            // The rolling foot: flat through mid-stance, then the heel lifts
            // over the last sixth so the toe pivots into toe-off.
            val heelLift = if (p < 0.5f) 0f else 0.06f * (1f - cos((p - 0.5f) / 0.1f * PI.toFloat())) * 0.5f
            val ankleHeight = 0.10f + heelLift
            // Two-link IK from the hip to the planted ankle; the knee bends
            // forward like a human knee.
            val reach = hipY - ankleHeight
            val cosKnee = (
                (THIGH * THIGH + SHANK * SHANK - reach * reach) /
                    (2f * THIGH * SHANK)
                ).coerceIn(-1f, 1f)
            val kneeFlex = PI.toFloat() - acos(cosKnee)
            val thighAngle = atan2(
                SHANK * sin(kneeFlex),
                THIGH + SHANK * cos(kneeFlex),
            )
            knee = Sagittal(
                up = hipY - THIGH * cos(thighAngle),
                forward = -THIGH * sin(thighAngle),
            )
            ankle = Sagittal(up = ankleHeight, forward = 0f)
        } else {
            val shankAngle = hipAngle - kneeAngle
            knee = Sagittal(
                up = hipY - THIGH * cos(hipAngle),
                forward = -THIGH * sin(hipAngle),
            )
            ankle = Sagittal(
                up = knee.up - SHANK * cos(shankAngle),
                forward = knee.forward - SHANK * sin(shankAngle),
            )
        }
        // The foot rides the ankle's sagittal tilt; the toe stays above the
        // floor through the swing clearance and the heel-off pivot.
        val shankTilt = atan2(-(ankle.forward - knee.forward), knee.up - ankle.up)
        val footPitch = shankTilt - ankleAngle - 8f * DEG
        val toe = Sagittal(
            up = (ankle.up + FOOT * sin(footPitch)).coerceAtLeast(0.02f),
            forward = ankle.forward + FOOT * cos(footPitch),
        )
        return Triple(knee, ankle, toe)
    }

    fun legJoints(p: Float, sideX: Float, left: Boolean): Map<Joint, Joint3> {
        val (knee, ankle, toe) = leg(p)
        val toJoint: (Sagittal) -> Joint3 = { point -> Joint3(sideX, point.up, point.forward) }
        return if (left) {
            mapOf(
                Joint.LEFT_KNEE to toJoint(knee),
                Joint.LEFT_ANKLE to toJoint(ankle),
                Joint.LEFT_TOE to toJoint(toe),
            )
        } else {
            mapOf(
                Joint.RIGHT_KNEE to toJoint(knee),
                Joint.RIGHT_ANKLE to toJoint(ankle),
                Joint.RIGHT_TOE to toJoint(toe),
            )
        }
    }

    val rightLeg = legJoints(phase, 0.12f, left = false)
    val leftLeg = legJoints(phase + 0.5f, -0.12f, left = true)

    val shoulderY = hipY + 0.57f * cos(lean)
    val shoulderForward = -0.57f * sin(lean)
    val neckY = hipY + 0.65f * cos(lean)
    val neckForward = -0.65f * sin(lean)
    val headY = hipY + 0.86f * cos(lean)
    val headForward = -0.86f * sin(lean) + 0.02f

    // Arms counter-swing: the right arm follows the left leg and vice versa,
    // with the elbow bending a little more as the arm swings forward.
    fun arm(sideX: Float, swingPhase: Float): Pair<Joint3, Joint3> {
        val swing = curveAt(hipCurve, swingPhase) * DEG * 0.45f
        val bend = 0.5f + 0.25f * abs(sin(swing))
        val elbow = Sagittal(
            up = shoulderY - UPPER_ARM * cos(swing),
            forward = shoulderForward - UPPER_ARM * sin(swing),
        )
        val wrist = Sagittal(
            up = elbow.up - FOREARM * cos(swing + bend),
            forward = elbow.forward - FOREARM * sin(swing + bend),
        )
        return Pair(
            Joint3(sideX, elbow.up, elbow.forward),
            Joint3(sideX, wrist.up, wrist.forward),
        )
    }
    val (rightElbow, rightWrist) = arm(0.25f, phase + 0.5f)
    val (leftElbow, leftWrist) = arm(-0.25f, phase)

    return mapOf(
        Joint.HEAD to Joint3(0f, headY, headForward),
        Joint.NECK to Joint3(0f, neckY, neckForward),
        Joint.LEFT_SHOULDER to Joint3(-0.19f, shoulderY, shoulderForward),
        Joint.RIGHT_SHOULDER to Joint3(0.19f, shoulderY, shoulderForward),
        Joint.LEFT_ELBOW to leftElbow,
        Joint.RIGHT_ELBOW to rightElbow,
        Joint.LEFT_WRIST to leftWrist,
        Joint.RIGHT_WRIST to rightWrist,
        Joint.LEFT_HIP to Joint3(-0.12f, hipY, 0f),
        Joint.RIGHT_HIP to Joint3(0.12f, hipY, 0f),
    ) + leftLeg + rightLeg
}
