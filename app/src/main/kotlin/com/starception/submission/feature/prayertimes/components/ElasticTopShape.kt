/*
 * Enhanced ElasticTopShape for Prayer Times Pull-to-Refresh
 * Adapted from Android Platform Samples Haptics Wobble Demo
 */
package com.starception.submission.feature.prayertimes.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rectangular shape with an elastic top for pull-to-refresh animations.
 *
 * @param elasticTopPercent Decimal value between -1 and 1 that represents how to draw the
 *     top of the elastic shape. A negative value rises the elastic up (-1 is max up), a zero value
 *     is flat, and a positive value pushes the elastic band down (1 is max down).
 *
 * Note: When sizing the shape keep in mind the rectangle leaves space at the top for the
 * elastic to rise up. For example if you size 100.dp, the height of the shape with a flat top
 * (elasticTopPercent = 0) will only have a height of 50.dp.
 */
class ElasticTopShape(private val elasticTopPercent: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val elasticTopPercent = elasticTopPercent.coerceIn(-1f, 1f)

        // Need drawn shape to have space for elastic convex top to be drawn.
        val heightOfBaseRectangle = size.height / 2
        val halfWidthOfBaseRectangle = size.width / 2

        // The float values that represent values for the elastic band to be full up or fully down.
        val maxBezierControlPointYElasticUp = -(heightOfBaseRectangle)
        // 5f is a buffer so user can see a little bit of the elastic band when fully down.
        val maxBezierControlPointElasticDown = size.height + heightOfBaseRectangle - 5f

        val bezierControlPointY = if (elasticTopPercent < 0) {
            maxBezierControlPointYElasticUp + ((1 + elasticTopPercent) * size.height)
        } else if (elasticTopPercent == 0f) {
            heightOfBaseRectangle
        } else {
            maxBezierControlPointElasticDown - (1f - elasticTopPercent) *
                (maxBezierControlPointElasticDown - heightOfBaseRectangle)
        }

        val path = Path().apply {
            moveTo(0f, heightOfBaseRectangle)
            quadraticBezierTo(
                halfWidthOfBaseRectangle, // Half of width as bezier X control point for symmetry.
                bezierControlPointY,
                size.width, // curve to this X coordinate.
                heightOfBaseRectangle // curve to this Y coordinate.
            )
            lineTo(size.width, size.height) // Draw right hand edge of rectangle.
            lineTo(0f, size.height) // Draw bottom edge of rectangle.
            lineTo(0f, heightOfBaseRectangle) // Draw left hand edge of rectangle.
            close()
        }
        return Outline.Generic(path)
    }
}