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

package com.starception.submission.widget.samples.utils

/**
 * Set of aspect ratios generally used for images in a widget
 *
 * Note that, images not in the selected ratio are cropped for display by design.
 */
@JvmInline
value class AspectRatio private constructor(private val value: Int) {
    companion object {
        /**
         * The aspect ratio of 1 x 1.
         */
        val Ratio1x1: AspectRatio = AspectRatio(0)

        /**
         * The aspect ratio of 16 x 9.
         */
        val Ratio16x9: AspectRatio = AspectRatio(1)

        /**
         * The aspect ratio of 2 x 3.
         */
        val Ratio2x3: AspectRatio = AspectRatio(2)

        /**
         * The aspect ratio of 4 x 3.
         */
        val Ratio4x3: AspectRatio = AspectRatio(3)

        /**
         * Returns the aspect ration as [Double]
         */
        fun AspectRatio.asDouble(): Double {
            return when (this) {
                Ratio1x1 -> 1f
                Ratio16x9 -> 16f / 9f
                Ratio2x3 -> 2f / 3f
                Ratio4x3 -> 4f / 3f
                else -> throw IllegalArgumentException("Unknown Aspect ration $this")
            }.toDouble()
        }
    }
}
