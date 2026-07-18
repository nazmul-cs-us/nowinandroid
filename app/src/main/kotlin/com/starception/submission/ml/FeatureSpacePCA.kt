package com.starception.submission.ml

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * On-device PCA of the 30-D window features down to 3-D, for the feature-space
 * visualization mode. Well-separated posture clusters in this projection mean the
 * features can discriminate the classes; overlapping clusters explain confusions.
 *
 * Pure Kotlin: standardize -> covariance (30x30) -> top-3 eigenvectors by power
 * iteration with deflation -> project -> rescale to a stable visual range.
 * ~1k samples x 30 features runs in well under a second on-device.
 */
object FeatureSpacePCA {

    data class Result(
        /** x,y,z triplets, parallel to the input sample list. */
        val positions: FloatArray,
        /** Fraction of total variance captured by the 3 components (0..1). */
        val varianceExplained: Float,
    )

    private const val DIMS = 30
    private const val COMPONENTS = 3
    private const val POWER_ITERATIONS = 60

    /** Half-extent of the visual cube the projection is scaled into. */
    private const val VISUAL_RANGE = 12f

    fun project(samples: List<SalahDataSample>): Result {
        val n = samples.size
        if (n < 2) return Result(FloatArray(n * 3), 0f)

        // Extract + standardize features (z-score, so no single unit dominates)
        val data = Array(n) { SalahFeatureExtractor.extractFeatures(samples[it]).copyOf() }
        val mean = DoubleArray(DIMS)
        val std = DoubleArray(DIMS)
        for (j in 0 until DIMS) {
            var s = 0.0
            for (i in 0 until n) s += data[i][j]
            mean[j] = s / n
            var v = 0.0
            for (i in 0 until n) {
                val d = data[i][j] - mean[j]
                v += d * d
            }
            // Sample std (n-1) to match the covariance denominator below, so the
            // standardized covariance is exactly the correlation matrix and the
            // variance-explained fraction is unbiased.
            std[j] = sqrt(v / (n - 1)).coerceAtLeast(1e-7)
        }
        val z = Array(n) { i -> DoubleArray(DIMS) { j -> (data[i][j] - mean[j]) / std[j] } }

        // Covariance matrix (standardized -> this is the correlation matrix; its
        // total variance is exactly DIMS)
        val cov = Array(DIMS) { DoubleArray(DIMS) }
        for (i in 0 until n) {
            val row = z[i]
            for (a in 0 until DIMS) {
                val ra = row[a]
                for (b in a until DIMS) {
                    cov[a][b] += ra * row[b]
                }
            }
        }
        for (a in 0 until DIMS) {
            for (b in a until DIMS) {
                cov[a][b] /= (n - 1)
                cov[b][a] = cov[a][b]
            }
        }

        // Top-3 eigenpairs via power iteration + deflation
        val components = Array(COMPONENTS) { DoubleArray(DIMS) }
        val eigenvalues = DoubleArray(COMPONENTS)
        val work = Array(DIMS) { cov[it].copyOf() }
        for (c in 0 until COMPONENTS) {
            var v = DoubleArray(DIMS) { if (it == c) 1.0 else 0.5 / (it + 1) }
            normalize(v)
            repeat(POWER_ITERATIONS) {
                v = multiply(work, v)
                normalize(v)
            }
            val av = multiply(work, v)
            var lambda = 0.0
            for (j in 0 until DIMS) lambda += v[j] * av[j]
            components[c] = v
            eigenvalues[c] = lambda.coerceAtLeast(0.0)
            // Deflate: work -= lambda * v v^T
            for (a in 0 until DIMS) {
                for (b in 0 until DIMS) {
                    work[a][b] -= lambda * v[a] * v[b]
                }
            }
        }

        // Project and find max extent for visual scaling
        val projected = FloatArray(n * 3)
        var maxAbs = 1e-7
        for (i in 0 until n) {
            for (c in 0 until COMPONENTS) {
                var p = 0.0
                for (j in 0 until DIMS) p += z[i][j] * components[c][j]
                projected[i * 3 + c] = p.toFloat()
                if (abs(p) > maxAbs) maxAbs = abs(p)
            }
        }
        val scale = (VISUAL_RANGE / maxAbs).toFloat()
        for (k in projected.indices) projected[k] *= scale

        val variance = (eigenvalues.sum() / DIMS).toFloat().coerceIn(0f, 1f)
        return Result(projected, variance)
    }

    private fun normalize(v: DoubleArray) {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm).coerceAtLeast(1e-12)
        for (i in v.indices) v[i] /= norm
    }

    private fun multiply(m: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val out = DoubleArray(v.size)
        for (a in v.indices) {
            var s = 0.0
            val row = m[a]
            for (b in v.indices) s += row[b] * v[b]
            out[a] = s
        }
        return out
    }
}
