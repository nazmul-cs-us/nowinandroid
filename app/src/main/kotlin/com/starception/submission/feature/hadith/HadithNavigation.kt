package com.starception.submission.feature.hadith

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Route for Hadith detail screen
 * @param collectionName The collection name (e.g., "Bukhari", "Muslim")
 * @param hadithNumber The hadith number in the collection
 * @param databaseFile The database file name
 */
@Serializable
data class HadithDetailRoute(
    val collectionName: String,
    val hadithNumber: Int,
    val databaseFile: String
)

/**
 * Navigate to Hadith detail screen
 */
fun NavController.navigateToHadithDetail(
    collectionName: String,
    hadithNumber: Int,
    databaseFile: String,
    navOptions: NavOptions? = null
) {
    val encodedCollection = URLEncoder.encode(collectionName, "UTF-8")
    val encodedDbFile = URLEncoder.encode(databaseFile, "UTF-8")
    navigate(
        route = HadithDetailRoute(
            collectionName = encodedCollection,
            hadithNumber = hadithNumber,
            databaseFile = encodedDbFile
        ),
        navOptions = navOptions
    )
}

/**
 * Add Hadith detail screen to navigation graph
 */
fun NavGraphBuilder.hadithDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToPreviousHadith: (collectionName: String, currentHadithNumber: Int, databaseFile: String) -> Unit = { _, _, _ -> },
    onNavigateToNextHadith: (collectionName: String, currentHadithNumber: Int, databaseFile: String) -> Unit = { _, _, _ -> }
) {
    composable<HadithDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<HadithDetailRoute>()
        val decodedCollection = URLDecoder.decode(route.collectionName, "UTF-8")
        val decodedDbFile = URLDecoder.decode(route.databaseFile, "UTF-8")

        HadithDetailScreen(
            collectionName = decodedCollection,
            hadithNumber = route.hadithNumber,
            databaseFile = decodedDbFile,
            onBackClick = onBackClick,
            onNavigateToPreviousHadith = {
                onNavigateToPreviousHadith(decodedCollection, route.hadithNumber, decodedDbFile)
            },
            onNavigateToNextHadith = {
                onNavigateToNextHadith(decodedCollection, route.hadithNumber, decodedDbFile)
            }
        )
    }
}
