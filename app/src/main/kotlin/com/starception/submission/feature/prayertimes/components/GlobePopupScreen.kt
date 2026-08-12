package com.starception.submission.feature.prayertimes.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetDragHandle
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import com.starception.submission.feature.prayertimes.utils.calculateQiblaDirection
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView

/**
 * Modal bottom sheet containing the live Qibla globe.
 *
 * The public name is retained so existing tile variants keep one entry point,
 * but this is now a Material 3 sheet with native swipe-down dismissal rather
 * than a custom Dialog popup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobePopupScreen(
    userLatitude: Double,
    userLongitude: Double,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val landscapeContentHeight = (configuration.screenHeightDp.dp * 0.84f)
        .coerceIn(300.dp, 480.dp)
    val portraitGlobeHeight = ((configuration.screenWidthDp.dp - 44.dp) * 1.10f)
        .coerceIn(300.dp, 450.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val distanceKm = remember(userLatitude, userLongitude) {
        distanceToMakkahKm(userLatitude, userLongitude)
    }
    val qiblaBearing = remember(userLatitude, userLongitude) {
        calculateQiblaDirection(userLatitude, userLongitude).toInt().floorMod(360)
    }
    val cardinalDirection = remember(qiblaBearing) {
        cardinalDirection(qiblaBearing)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLandscape) Modifier.height(landscapeContentHeight)
                            else Modifier.wrapContentHeight(),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    NiaBottomSheetDragHandle(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 16.dp),
                    )

                    Text(
                        text = "Qibla direction",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "$qiblaBearing° $cardinalDirection · ${formatDistance(distanceKm)} km to Makkah",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isLandscape) Modifier.weight(1f)
                                else Modifier.height(portraitGlobeHeight),
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF070B10),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        QiblaGlobeView(
                            userLatitude = userLatitude,
                            userLongitude = userLongitude,
                            showControls = true,
                            isActiveTile = true,
                            surfaceCornerRadius = 24.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    }
                }
            }
        }
    }
}

private const val MAKKAH_LAT = 21.4225
private const val MAKKAH_LON = 39.8262
private const val EARTH_RADIUS_KM = 6371.0

private fun distanceToMakkahKm(latitude: Double, longitude: Double): Int {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(MAKKAH_LAT)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(MAKKAH_LON - longitude)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
        kotlin.math.sin(dLon / 2).let { it * it }
    val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return (EARTH_RADIUS_KM * c).toInt()
}

private fun formatDistance(km: Int): String =
    if (km >= 1000) "%,d".format(km) else km.toString()

private fun cardinalDirection(bearing: Int): String {
    val directions = listOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
    return directions[((bearing + 22.5) / 45.0).toInt() % directions.size]
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
