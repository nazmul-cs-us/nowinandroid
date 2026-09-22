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
import com.starception.submission.core.model.data.ShamayelBooks
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
    val shufflePlayback: Boolean = false,
)

@Serializable
data class BukhariBookRoute(val bookId: Int)

@Serializable
data class ShamayelBookRoute(val bookId: Int)

fun NavController.navigateToBukhariBook(bookId: Int) {
    navigate(BukhariBookRoute(bookId))
}

fun NavController.navigateToShamayelBook(bookId: Int) {
    navigate(ShamayelBookRoute(bookId))
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

/** Opens the first hadith in a Shama'il book and continuously plays only that book's range. */
fun NavController.navigateToShamayelBookPlayback(bookId: Int) {
    val book = ShamayelBooks.find(bookId) ?: return
    navigateToHadithDetail(
        collectionName = "Shamai'l At-Tirmidhi",
        hadithNumber = book.firstHadithId,
        databaseFile = "shamayele_tirmidhi_complete.db",
        autoPlay = true,
        autoAdvance = true,
        playbackRangeStart = book.firstHadithId,
        playbackRangeEnd = book.lastHadithId,
    )
}

fun NavController.navigateToBukhariCollectionPlayback(shuffle: Boolean = false) {
    navigateToHadithDetail(
        collectionName = "Sahih Bukhari",
        hadithNumber = 1,
        databaseFile = "sahih_bukhari.db",
        autoPlay = true,
        autoAdvance = true,
        playbackRangeStart = 1,
        playbackRangeEnd = BukhariBooks.all.maxOf { it.lastHadithId },
        shufflePlayback = shuffle,
    )
}

fun NavController.navigateToShamayelCollectionPlayback(shuffle: Boolean = false) {
    navigateToHadithDetail(
        collectionName = "Shamai'l At-Tirmidhi",
        hadithNumber = 1,
        databaseFile = "shamayele_tirmidhi_complete.db",
        autoPlay = true,
        autoAdvance = true,
        playbackRangeStart = 1,
        playbackRangeEnd = ShamayelBooks.all.maxOf { it.lastHadithId },
        shufflePlayback = shuffle,
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
    shufflePlayback: Boolean = false,
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
            shufflePlayback = shufflePlayback,
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
            shufflePlayback = route.shufflePlayback,
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

fun NavGraphBuilder.shamayelBookScreen(
    onBackClick: () -> Unit,
    onHadithClick: (Int) -> Unit,
    onPlayAllClick: (Int) -> Unit,
) {
    composable<ShamayelBookRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<ShamayelBookRoute>()
        ShamayelBookScreen(
            bookId = route.bookId,
            onBackClick = onBackClick,
            onHadithClick = onHadithClick,
            onPlayAllClick = { onPlayAllClick(route.bookId) },
        )
    }
}
