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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.starception.submission.shared.ml.SalahPosture

/**
 * The 3-D scene bridge implemented natively per platform — Swift3D on iOS,
 * the same contract the Android app's SceneView figure fulfils. Implemented
 * by the Swift `SalahScene3DHost`; injected at the Compose root like
 * [com.starception.submission.shared.ml.SalahTfliteService].
 */
interface Salah3DSceneService {
    /** 0 = humanoid, 1 = scatter, 2 = gravity, 3 = feature PCA. */
    fun setSceneMode(mode: Int)

    /** 16 joints x (x, y, z) — the current skeleton pose target. */
    fun updatePose(joints: List<Float>, postureIndex: Int)

    /** The full point cloud: 3N floats plus one posture ordinal per point. */
    fun updateScatter(points: List<Float>, postureIndices: List<Int>)

    /** The playhead's current 3-D position, or an empty list when hidden. */
    fun updatePlayhead(position: List<Float>)

    /** The device gravity direction, in g units against the world frame. */
    fun updateGravity(vector: List<Float>)
}

/**
 * Hosts the platform 3-D scene for the salah session review. The iOS actual
 * embeds the Swift3D-rendered view; platforms without a native scene render
 * nothing and the review falls back to the shared Canvas projection.
 */
@Composable
internal expect fun Salah3DSceneHost(service: Salah3DSceneService, modifier: Modifier)

// ─────────────────── shared keyframe skeleton ───────────────────

/** Android's articulated joints — the same 16 the SceneView figure uses. */
internal enum class Joint {
    HEAD, NECK,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_TOE, RIGHT_TOE,
}

internal data class Joint3(val x: Float, val y: Float, val z: Float)

internal typealias SkeletonJoints = Map<Joint, Joint3>

/** Android's per-posture keyframe poses, ported verbatim. */
internal fun skeletonPoseFor(posture: SalahPosture): SkeletonJoints {
    fun p(x: Float, y: Float, z: Float) = Joint3(x, y, z)
    return when (posture) {
        SalahPosture.QIYAM, SalahPosture.RISING_TO_QIYAM, SalahPosture.NOT_PRAYING -> mapOf(
            Joint.HEAD to p(0f, 1.78f, 0f), Joint.NECK to p(0f, 1.57f, 0f),
            Joint.LEFT_SHOULDER to p(-0.19f, 1.49f, 0f), Joint.RIGHT_SHOULDER to p(0.19f, 1.49f, 0f),
            Joint.LEFT_ELBOW to p(-0.25f, 1.22f, -0.03f), Joint.RIGHT_ELBOW to p(0.25f, 1.22f, -0.03f),
            Joint.LEFT_WRIST to p(0.06f, 1.08f, -0.16f), Joint.RIGHT_WRIST to p(-0.06f, 1.04f, -0.18f),
            Joint.LEFT_HIP to p(-0.12f, 0.92f, 0f), Joint.RIGHT_HIP to p(0.12f, 0.92f, 0f),
            Joint.LEFT_KNEE to p(-0.13f, 0.5f, 0f), Joint.RIGHT_KNEE to p(0.13f, 0.5f, 0f),
            Joint.LEFT_ANKLE to p(-0.13f, 0.1f, 0f), Joint.RIGHT_ANKLE to p(0.13f, 0.1f, 0f),
            Joint.LEFT_TOE to p(-0.13f, 0.05f, -0.2f), Joint.RIGHT_TOE to p(0.13f, 0.05f, -0.2f),
        )
        SalahPosture.QIYAM_RISING -> mapOf(
            Joint.HEAD to p(0f, 1.7f, -0.06f), Joint.NECK to p(0f, 1.5f, -0.06f),
            Joint.LEFT_SHOULDER to p(-0.19f, 1.43f, -0.06f), Joint.RIGHT_SHOULDER to p(0.19f, 1.43f, -0.06f),
            Joint.LEFT_ELBOW to p(-0.29f, 1.16f, -0.08f), Joint.RIGHT_ELBOW to p(0.29f, 1.16f, -0.08f),
            Joint.LEFT_WRIST to p(-0.29f, 0.9f, -0.08f), Joint.RIGHT_WRIST to p(0.29f, 0.9f, -0.08f),
            Joint.LEFT_HIP to p(-0.12f, 0.9f, 0.03f), Joint.RIGHT_HIP to p(0.12f, 0.9f, 0.03f),
            Joint.LEFT_KNEE to p(-0.13f, 0.49f, 0f), Joint.RIGHT_KNEE to p(0.13f, 0.49f, 0f),
            Joint.LEFT_ANKLE to p(-0.13f, 0.1f, 0f), Joint.RIGHT_ANKLE to p(0.13f, 0.1f, 0f),
            Joint.LEFT_TOE to p(-0.13f, 0.05f, -0.2f), Joint.RIGHT_TOE to p(0.13f, 0.05f, -0.2f),
        )
        SalahPosture.RUKU -> mapOf(
            Joint.HEAD to p(0f, 1.02f, -0.72f), Joint.NECK to p(0f, 1.04f, -0.51f),
            Joint.LEFT_SHOULDER to p(-0.2f, 1.06f, -0.43f), Joint.RIGHT_SHOULDER to p(0.2f, 1.06f, -0.43f),
            Joint.LEFT_ELBOW to p(-0.24f, 0.82f, -0.28f), Joint.RIGHT_ELBOW to p(0.24f, 0.82f, -0.28f),
            Joint.LEFT_WRIST to p(-0.15f, 0.58f, -0.12f), Joint.RIGHT_WRIST to p(0.15f, 0.58f, -0.12f),
            Joint.LEFT_HIP to p(-0.13f, 0.91f, 0.08f), Joint.RIGHT_HIP to p(0.13f, 0.91f, 0.08f),
            Joint.LEFT_KNEE to p(-0.14f, 0.5f, 0f), Joint.RIGHT_KNEE to p(0.14f, 0.5f, 0f),
            Joint.LEFT_ANKLE to p(-0.14f, 0.1f, 0f), Joint.RIGHT_ANKLE to p(0.14f, 0.1f, 0f),
            Joint.LEFT_TOE to p(-0.14f, 0.05f, -0.2f), Joint.RIGHT_TOE to p(0.14f, 0.05f, -0.2f),
        )
        SalahPosture.GOING_TO_SUJUD -> mapOf(
            Joint.HEAD to p(0f, 0.97f, -0.47f), Joint.NECK to p(0f, 0.93f, -0.29f),
            Joint.LEFT_SHOULDER to p(-0.19f, 0.91f, -0.23f), Joint.RIGHT_SHOULDER to p(0.19f, 0.91f, -0.23f),
            Joint.LEFT_ELBOW to p(-0.27f, 0.6f, -0.33f), Joint.RIGHT_ELBOW to p(0.27f, 0.6f, -0.33f),
            Joint.LEFT_WRIST to p(-0.27f, 0.27f, -0.49f), Joint.RIGHT_WRIST to p(0.27f, 0.27f, -0.49f),
            Joint.LEFT_HIP to p(-0.13f, 0.66f, 0.09f), Joint.RIGHT_HIP to p(0.13f, 0.66f, 0.09f),
            Joint.LEFT_KNEE to p(-0.17f, 0.26f, -0.03f), Joint.RIGHT_KNEE to p(0.17f, 0.26f, -0.03f),
            Joint.LEFT_ANKLE to p(-0.16f, 0.09f, 0.35f), Joint.RIGHT_ANKLE to p(0.16f, 0.09f, 0.35f),
            Joint.LEFT_TOE to p(-0.16f, 0.05f, 0.53f), Joint.RIGHT_TOE to p(0.16f, 0.05f, 0.53f),
        )
        SalahPosture.SUJUD -> mapOf(
            Joint.HEAD to p(0f, 0.18f, -0.62f), Joint.NECK to p(0f, 0.3f, -0.43f),
            Joint.LEFT_SHOULDER to p(-0.2f, 0.34f, -0.31f), Joint.RIGHT_SHOULDER to p(0.2f, 0.34f, -0.31f),
            Joint.LEFT_ELBOW to p(-0.29f, 0.19f, -0.38f), Joint.RIGHT_ELBOW to p(0.29f, 0.19f, -0.38f),
            Joint.LEFT_WRIST to p(-0.27f, 0.08f, -0.61f), Joint.RIGHT_WRIST to p(0.27f, 0.08f, -0.61f),
            Joint.LEFT_HIP to p(-0.13f, 0.57f, 0.18f), Joint.RIGHT_HIP to p(0.13f, 0.57f, 0.18f),
            Joint.LEFT_KNEE to p(-0.17f, 0.09f, 0.1f), Joint.RIGHT_KNEE to p(0.17f, 0.09f, 0.1f),
            Joint.LEFT_ANKLE to p(-0.16f, 0.08f, 0.53f), Joint.RIGHT_ANKLE to p(0.16f, 0.08f, 0.53f),
            Joint.LEFT_TOE to p(-0.16f, 0.05f, 0.7f), Joint.RIGHT_TOE to p(0.16f, 0.05f, 0.7f),
        )
        SalahPosture.JALSA, SalahPosture.TASHAHHUD -> mapOf(
            Joint.HEAD to p(0f, 1.18f, 0.02f), Joint.NECK to p(0f, 0.98f, 0f),
            Joint.LEFT_SHOULDER to p(-0.19f, 0.91f, 0f), Joint.RIGHT_SHOULDER to p(0.19f, 0.91f, 0f),
            Joint.LEFT_ELBOW to p(-0.24f, 0.67f, -0.02f), Joint.RIGHT_ELBOW to p(0.24f, 0.67f, -0.02f),
            Joint.LEFT_WRIST to p(-0.18f, 0.49f, -0.25f), Joint.RIGHT_WRIST to p(0.18f, 0.49f, -0.25f),
            Joint.LEFT_HIP to p(-0.13f, 0.41f, 0.12f), Joint.RIGHT_HIP to p(0.13f, 0.41f, 0.12f),
            Joint.LEFT_KNEE to p(-0.2f, 0.16f, -0.29f), Joint.RIGHT_KNEE to p(0.2f, 0.16f, -0.29f),
            Joint.LEFT_ANKLE to p(-0.18f, 0.08f, 0.37f), Joint.RIGHT_ANKLE to p(0.18f, 0.08f, 0.37f),
            Joint.LEFT_TOE to p(-0.18f, 0.05f, 0.55f), Joint.RIGHT_TOE to p(0.18f, 0.05f, 0.55f),
        )
    }
}

internal fun lerpSkeletonPose(from: SkeletonJoints, to: SkeletonJoints, amount: Float): SkeletonJoints =
    to.mapValues { (joint, target) ->
        val current = from[joint] ?: target
        Joint3(
            current.x + (target.x - current.x) * amount,
            current.y + (target.y - current.y) * amount,
            current.z + (target.z - current.z) * amount,
        )
    }

/** The 48 floats the Swift3D scene expects — joints in [Joint] declaration order. */
internal fun SkeletonJoints.flatten(): List<Float> =
    Joint.entries.flatMap { joint ->
        val p = getValue(joint)
        listOf(p.x, p.y, p.z)
    }
