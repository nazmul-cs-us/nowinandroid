package com.starception.submission.feature.salah.visualization

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ShapeProportionModelTest {

    private val referenceMeasurements = BodyMeasurements(
        headAndNeckHeight = 0.45f,
        upperBodyHeight = 0.62f,
        lowerBodyHeight = 0.82f,
        abdomenBreadth = 0.27f,
        shoulderBreadth = 0.38f,
        shoulderCurve = 0.20f,
        armAngleDegrees = 24f,
        legAngleDegrees = 14f,
    )

    @Test
    fun paperFeaturesUseDefinedRatios() {
        val features = referenceMeasurements.toFeatures()

        assertNear(0.45f / (0.62f + 0.82f), features.headToBody)
        assertNear(0.38f / 0.27f, features.shoulderToAbdomen)
        assertNear(0.62f / 0.82f, features.upperToLowerBody)
        assertNear(0.20f, features.shoulderCurve)
        assertNear(24f, features.armAngle)
        assertNear(14f, features.legAngle)
    }

    @Test
    fun everySynthesizedShapeVectorSumsToOne() {
        val features = referenceMeasurements.toFeatures()

        BodyShapeStyle.entries.forEach { style ->
            val shape = ShapeProportionModel.synthesize(features, style)
            assertWeightsNormalized(shape.torsoWeights)
            assertWeightsNormalized(shape.abdomenWeights)
        }
    }

    @Test
    fun bodyFamiliesProduceDistinctProportions() {
        val features = referenceMeasurements.toFeatures()
        val thin = ShapeProportionModel.synthesize(features, BodyShapeStyle.THIN)
        val full = ShapeProportionModel.synthesize(features, BodyShapeStyle.FULL)
        val muscular = ShapeProportionModel.synthesize(features, BodyShapeStyle.MUSCULAR)

        assertTrue(full.abdomenWidthScale > muscular.abdomenWidthScale)
        assertTrue(muscular.torsoWidthScale > thin.torsoWidthScale)
        assertTrue(full.abdomenWeights.circle > thin.abdomenWeights.circle)
        assertTrue(muscular.torsoWeights.triangle > full.torsoWeights.triangle)
    }

    private fun assertWeightsNormalized(weights: PrimitiveShapeWeights) {
        assertNear(1f, weights.square + weights.circle + weights.triangle)
        assertTrue(weights.square > 0f)
        assertTrue(weights.circle > 0f)
        assertTrue(weights.triangle > 0f)
    }

    private fun assertNear(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.0001f, "Expected $expected, got $actual")
    }
}
