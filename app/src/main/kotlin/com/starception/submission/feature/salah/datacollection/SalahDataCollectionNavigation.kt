package com.starception.submission.feature.salah.datacollection

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
object SalahDataCollectionRoute

fun NavController.navigateToSalahDataCollection() = navigate(SalahDataCollectionRoute)

fun NavGraphBuilder.salahDataCollectionScreen(
    onBackClick: () -> Unit
) {
    composable<SalahDataCollectionRoute> {
        SalahDataCollectionScreen(
            onBackClick = onBackClick
        )
    }
}
