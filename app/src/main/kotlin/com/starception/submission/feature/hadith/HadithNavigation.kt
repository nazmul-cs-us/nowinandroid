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

package com.starception.submission.feature.hadith

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.core.model.data.ShamayelBooks
import com.starception.submission.navigation.detailEnterTransition
import com.starception.submission.navigation.detailExitTransition
import com.starception.submission.navigation.detailPopEnterTransition
import com.starception.submission.navigation.detailPopExitTransition
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

/**
 * Book browser route for any registry collection (see [com.starception.submission
 * .core.model.data.HadithCollections]) — the generalized form of
 * [BukhariBookRoute]/[ShamayelBookRoute].
 */
@Serializable
data class HadithCollectionBookRoute(
    val databaseFile: String,
    val bookId: Int,
)

fun NavController.navigateToBukhariBook(bookId: Int) {
    navigate(BukhariBookRoute(bookId))
}

fun NavController.navigateToShamayelBook(bookId: Int) {
    navigate(ShamayelBookRoute(bookId))
}

/** Opens the book browser for any registry collection. */
fun NavController.navigateToHadithCollectionBook(databaseFile: String, bookId: Int) {
    navigate(HadithCollectionBookRoute(databaseFile, bookId))
}

/** Opens the first hadith of any registry collection's book and continuously plays its range. */
fun NavController.navigateToHadithCollectionBookPlayback(databaseFile: String, bookId: Int) {
    val collection = HadithCollections.byDatabaseFile(databaseFile) ?: return
    val book = collection.books.firstOrNull { it.id == bookId } ?: return
    navigateToHadithDetail(
        collectionName = collection.name,
        hadithNumber = book.firstHadithId,
        databaseFile = collection.databaseFile,
        autoPlay = true,
        autoAdvance = true,
        playbackRangeStart = book.firstHadithId,
        playbackRangeEnd = book.lastHadithId,
    )
}

/** Opens the first hadith of a whole registry collection and continuously plays it. */
fun NavController.navigateToHadithCollectionPlayback(databaseFile: String, shuffle: Boolean = false) {
    val collection = HadithCollections.byDatabaseFile(databaseFile) ?: return
    navigateToHadithDetail(
        collectionName = collection.name,
        hadithNumber = 1,
        databaseFile = collection.databaseFile,
        autoPlay = true,
        autoAdvance = true,
        playbackRangeStart = 1,
        playbackRangeEnd = collection.books.maxOf { it.lastHadithId },
        shufflePlayback = shuffle,
    )
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
    navOptions: NavOptions? = null,
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
        navOptions = navOptions,
    )
}

/**
 * Add Hadith detail screen to navigation graph
 */
fun NavGraphBuilder.hadithDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToPreviousHadith: (collectionName: String, currentHadithNumber: Int, databaseFile: String) -> Unit = { _, _, _ -> },
    onNavigateToNextHadith: (collectionName: String, currentHadithNumber: Int, databaseFile: String) -> Unit = { _, _, _ -> },
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
            },
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

/**
 * Book browser screen for any registry collection. The hadith-click callback receives the
 * resolved collection so the host navigates with the right collection name and database.
 */
fun NavGraphBuilder.hadithCollectionBookScreen(
    onBackClick: () -> Unit,
    onHadithClick: (collectionName: String, hadithNumber: Int, databaseFile: String) -> Unit,
    onPlayAllClick: (databaseFile: String, bookId: Int) -> Unit,
) {
    composable<HadithCollectionBookRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<HadithCollectionBookRoute>()
        val collection = HadithCollections.byDatabaseFile(route.databaseFile)
        HadithCollectionBookScreen(
            databaseFile = route.databaseFile,
            bookId = route.bookId,
            onBackClick = onBackClick,
            onHadithClick = { hadithNumber ->
                if (collection != null) {
                    onHadithClick(collection.name, hadithNumber, collection.databaseFile)
                }
            },
            onPlayAllClick = { onPlayAllClick(route.databaseFile, route.bookId) },
        )
    }
}
