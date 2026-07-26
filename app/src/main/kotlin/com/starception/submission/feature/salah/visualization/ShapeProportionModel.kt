package com.starception.submission.feature.salah.visualization

import kotlin.math.max

/** Body-shape families used by the paper's torso/abdomen synthesis examples. */
enum class BodyShapeStyle(val displayName: String) {
    REFERENCE("Reference"),
    THIN("Thin"),
    FULL("Full"),
    MUSCULAR("Muscular"),
}

/**
 * The six pose/proportion features from "Learning Shape-Proportion Relationships".
 * Angles are expressed in degrees; ratios and shoulderCurve are unitless.
 */
data class BodyProportionFeatures(
    val headToBody: Float,          // k1 = lh / (lu + ll)
    val shoulderToAbdomen: Float,   // k2 = bs / bh
    val upperToLowerBody: Float,    // k3 = lu / ll
    val shoulderCurve: Float,       // k4
    val armAngle: Float,            // k5 = theta-a
    val legAngle: Float,            // k6 = theta-l
)

data class BodyMeasurements(
    val headAndNeckHeight: Float,
    val upperBodyHeight: Float,
    val lowerBodyHeight: Float,
    val abdomenBreadth: Float,
    val shoulderBreadth: Float,
    val shoulderCurve: Float,
    val armAngleDegrees: Float,
    val legAngleDegrees: Float,
) {
    fun toFeatures(): BodyProportionFeatures {
        val bodyHeight = max(upperBodyHeight + lowerBodyHeight, EPSILON)
        return BodyProportionFeatures(
            headToBody = headAndNeckHeight / bodyHeight,
            shoulderToAbdomen = shoulderBreadth / max(abdomenBreadth, EPSILON),
            upperToLowerBody = upperBodyHeight / max(lowerBodyHeight, EPSILON),
            shoulderCurve = shoulderCurve,
            armAngle = armAngleDegrees,
            legAngle = legAngleDegrees,
        )
    }
}

/** Square/circle/triangle weights always normalized to a sum of one. */
data class PrimitiveShapeWeights(
    val square: Float,
    val circle: Float,
    val triangle: Float,
) {
    companion object {
        fun normalized(square: Float, circle: Float, triangle: Float): PrimitiveShapeWeights {
            val safeSquare = square.coerceAtLeast(MIN_SHAPE_WEIGHT)
            val safeCircle = circle.coerceAtLeast(MIN_SHAPE_WEIGHT)
            val safeTriangle = triangle.coerceAtLeast(MIN_SHAPE_WEIGHT)
            val total = safeSquare + safeCircle + safeTriangle
            return PrimitiveShapeWeights(
                square = safeSquare / total,
                circle = safeCircle / total,
                triangle = safeTriangle / total,
            )
        }
    }
}

data class SynthesizedBodyShape(
    val torsoWeights: PrimitiveShapeWeights,
    val abdomenWeights: PrimitiveShapeWeights,
    val torsoWidthScale: Float,
    val abdomenWidthScale: Float,
    val depthScale: Float,
)

/**
 * Deterministic 3D adaptation of the paper's shape-vector synthesis.
 *
 * The paper publishes the k1-k6 representation and one network's weights, but refers readers
 * to a now-unavailable companion site for the remaining torso/abdomen networks. These calibrated
 * coefficients preserve the published inputs and square/circle/triangle output representation
 * without pretending the omitted network parameters are available.
 */
object ShapeProportionModel {

    fun synthesize(
        features: BodyProportionFeatures,
        style: BodyShapeStyle,
    ): SynthesizedBodyShape {
        val base = baseShape(style)

        val headBalance = normalize(features.headToBody, center = 0.19f, span = 0.12f)
        val shoulderDominance = normalize(features.shoulderToAbdomen, center = 1.55f, span = 0.85f)
        val torsoLength = normalize(features.upperToLowerBody, center = 0.72f, span = 0.45f)
        val shoulderArc = normalize(features.shoulderCurve, center = 0.18f, span = 0.18f)
        val armSpread = normalize(features.armAngle, center = 28f, span = 65f)
        val legSpread = normalize(features.legAngle, center = 12f, span = 50f)

        // Each of the six paper features contributes to the primitive shape vector. Keep the
        // adjustments restrained so a person's selected body family remains visually stable
        // while their Salah pose changes.
        val torso = PrimitiveShapeWeights.normalized(
            square = base.torsoWeights.square + 0.06f * shoulderArc + 0.035f * torsoLength,
            circle = base.torsoWeights.circle + 0.035f * headBalance - 0.045f * shoulderDominance,
            triangle = base.torsoWeights.triangle + 0.10f * shoulderDominance + 0.025f * armSpread,
        )
        val abdomen = PrimitiveShapeWeights.normalized(
            square = base.abdomenWeights.square + 0.035f * torsoLength - 0.02f * legSpread,
            circle = base.abdomenWeights.circle - 0.035f * shoulderDominance + 0.025f * headBalance,
            triangle = base.abdomenWeights.triangle + 0.045f * shoulderDominance + 0.02f * legSpread,
        )

        return base.copy(
            torsoWeights = torso,
            abdomenWeights = abdomen,
            torsoWidthScale = (base.torsoWidthScale *
                (1f + 0.055f * shoulderDominance + 0.015f * armSpread)).coerceIn(0.68f, 1.42f),
            abdomenWidthScale = (base.abdomenWidthScale *
                (1f - 0.04f * shoulderDominance + 0.02f * legSpread)).coerceIn(0.62f, 1.48f),
            depthScale = (base.depthScale *
                (1f + 0.025f * headBalance + 0.02f * torsoLength)).coerceIn(0.72f, 1.38f),
        )
    }

    private fun baseShape(style: BodyShapeStyle): SynthesizedBodyShape = when (style) {
        BodyShapeStyle.REFERENCE -> SynthesizedBodyShape(
            torsoWeights = PrimitiveShapeWeights.normalized(0.30f, 0.34f, 0.36f),
            abdomenWeights = PrimitiveShapeWeights.normalized(0.30f, 0.46f, 0.24f),
            torsoWidthScale = 1f,
            abdomenWidthScale = 1f,
            depthScale = 1f,
        )
        BodyShapeStyle.THIN -> SynthesizedBodyShape(
            torsoWeights = PrimitiveShapeWeights.normalized(0.24f, 0.18f, 0.58f),
            abdomenWeights = PrimitiveShapeWeights.normalized(0.24f, 0.25f, 0.51f),
            torsoWidthScale = 0.84f,
            abdomenWidthScale = 0.76f,
            depthScale = 0.82f,
        )
        BodyShapeStyle.FULL -> SynthesizedBodyShape(
            torsoWeights = PrimitiveShapeWeights.normalized(0.17f, 0.70f, 0.13f),
            abdomenWeights = PrimitiveShapeWeights.normalized(0.12f, 0.79f, 0.09f),
            torsoWidthScale = 1.13f,
            abdomenWidthScale = 1.34f,
            depthScale = 1.22f,
        )
        BodyShapeStyle.MUSCULAR -> SynthesizedBodyShape(
            torsoWeights = PrimitiveShapeWeights.normalized(0.38f, 0.14f, 0.48f),
            abdomenWeights = PrimitiveShapeWeights.normalized(0.52f, 0.18f, 0.30f),
            torsoWidthScale = 1.21f,
            abdomenWidthScale = 0.97f,
            depthScale = 1.14f,
        )
    }

    private fun normalize(value: Float, center: Float, span: Float): Float =
        ((value - center) / span).coerceIn(-1f, 1f)
}

private const val EPSILON = 0.0001f
private const val MIN_SHAPE_WEIGHT = 0.02f
