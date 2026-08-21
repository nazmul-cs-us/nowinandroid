package com.starception.submission.feature.hadith

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.navigation.detailEnterTransition
import com.starception.submission.navigation.detailExitTransition
import com.starception.submission.navigation.detailPopEnterTransition
import com.starception.submission.navigation.detailPopExitTransition
import com.starception.submission.core.model.data.BukhariBooks
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
    val databaseFile: String,
    val autoPlay: Boolean = false,
    val autoAdvance: Boolean = false,
    val playbackRangeStart: Int? = null,
    val playbackRangeEnd: Int? = null,
)

@Serializable
data class BukhariBookRoute(val bookId: Int)

fun NavController.navigateToBukhariBook(bookId: Int) {
    navigate(BukhariBookRoute(bookId))
}

/** Opens the first hadith in a canonical book and continuously plays only that book's range. */
fun NavController.navigateToBukhariBookPlayback(bookId: Int) {
    val book = BukhariBooks.find(bookId) ?: return
    navigateToHadithDetail(
        collectionName = "Sahih Bukhari",
        hadithNumber = book.firstHadithId,
        databaseFile = "sahih_bukhari.db",
        autoPlay = true,
        autoAdvance = true,
        playbackRangeStart = book.firstHadithId,
        playbackRangeEnd = book.lastHadithId,
    )
}

/**
 * Navigate to Hadith detail screen
 */
fun NavController.navigateToHadithDetail(
    collectionName: String,
    hadithNumber: Int,
    databaseFile: String,
    autoPlay: Boolean = false,
    autoAdvance: Boolean = false,
    playbackRangeStart: Int? = null,
    playbackRangeEnd: Int? = null,
    navOptions: NavOptions? = null
) {
    val encodedCollection = URLEncoder.encode(collectionName, "UTF-8")
    val encodedDbFile = URLEncoder.encode(databaseFile, "UTF-8")
    navigate(
        route = HadithDetailRoute(
            collectionName = encodedCollection,
            hadithNumber = hadithNumber,
            databaseFile = encodedDbFile,
            autoPlay = autoPlay,
            autoAdvance = autoAdvance,
            playbackRangeStart = playbackRangeStart,
            playbackRangeEnd = playbackRangeEnd,
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
    composable<HadithDetailRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<HadithDetailRoute>()
        val decodedCollection = URLDecoder.decode(route.collectionName, "UTF-8")
        val decodedDbFile = URLDecoder.decode(route.databaseFile, "UTF-8")

        HadithDetailScreen(
            collectionName = decodedCollection,
            hadithNumber = route.hadithNumber,
            databaseFile = decodedDbFile,
            initialAutoPlay = route.autoPlay,
            initialAutoAdvance = route.autoAdvance,
            playbackRangeStart = route.playbackRangeStart,
            playbackRangeEnd = route.playbackRangeEnd,
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

fun NavGraphBuilder.bukhariBookScreen(
    onBackClick: () -> Unit,
    onHadithClick: (Int) -> Unit,
    onPlayAllClick: (Int) -> Unit,
) {
    composable<BukhariBookRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<BukhariBookRoute>()
        BukhariBookScreen(
            bookId = route.bookId,
            onBackClick = onBackClick,
            onHadithClick = onHadithClick,
            onPlayAllClick = { onPlayAllClick(route.bookId) },
        )
    }
}
