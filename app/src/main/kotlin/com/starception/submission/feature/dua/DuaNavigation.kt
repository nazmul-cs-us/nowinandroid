package com.starception.submission.feature.dua

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.navigation.detailEnterTransition
import com.starception.submission.navigation.detailExitTransition
import com.starception.submission.navigation.detailPopEnterTransition
import com.starception.submission.navigation.detailPopExitTransition
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Route for Dua detail screen
 * @param title The title of the dua
 * @param content The content of the dua (URL encoded)
 * @param quranReference Optional Quran reference (e.g., "2:127")
 * @param duaNumber The dua number (1-40 for Quranic duas)
 * @param newsResourceId The NiA news resource ID for bookmark sync (128 for dua 1, 129 for dua 2, etc.)
 * @param topicId Optional topic ID to filter duas for "Dua X of Y" display
 */
@Serializable
data class DuaDetailRoute(
    val title: String,
    val content: String,
    val quranReference: String? = null,
    val duaNumber: Int = 1,
    val newsResourceId: String = "",
    val topicId: String = ""
)

/**
 * Navigate to Dua detail screen
 */
fun NavController.navigateToDuaDetail(
    title: String,
    content: String,
    quranReference: String? = null,
    duaNumber: Int = 1,
    newsResourceId: String = "",
    topicId: String = "",
    navOptions: NavOptions? = null
) {
    val encodedContent = URLEncoder.encode(content, "UTF-8")
    val encodedTitle = URLEncoder.encode(title, "UTF-8")
    val encodedRef = quranReference?.let { URLEncoder.encode(it, "UTF-8") }
    // Deliberately left empty when the caller has no news id, rather than derived from
    // duaNumber. The old `127 + duaNumber` guess encoded an id layout that no longer
    // exists — for Quranic Dua 1 it yields 128, which is "Quranic Dua 28: In You we trust",
    // and for a Fortress dua it lands on an unrelated Quranic one entirely (Quranic ids are
    // 101–140, Fortress 1001–10083). Downstream that fabricated id drove page selection,
    // the topic tag and the bookmark toggle, all pointing at the wrong resource. An empty
    // value is honest: the screen skips id-matching and resolves by title or dua number,
    // and reads the real id off the dua it is showing.
    val resourceId = newsResourceId
    navigate(
        route = DuaDetailRoute(
            title = encodedTitle,
            content = encodedContent,
            quranReference = encodedRef,
            duaNumber = duaNumber,
            newsResourceId = resourceId,
            topicId = topicId
        ),
        navOptions = navOptions
    )
}

/**
 * Add Dua detail screen to navigation graph
 */
fun NavGraphBuilder.duaDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToSurah: ((surahNumber: Int, ayahNumber: Int) -> Unit)? = null,
    isBookmarked: (newsResourceId: String) -> Boolean = { false },
    onToggleBookmark: (newsResourceId: String) -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onHadithClick: ((collectionName: String, hadithNumber: Int, databaseFile: String) -> Unit)? = null
) {
    composable<DuaDetailRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<DuaDetailRoute>()
        val decodedTitle = URLDecoder.decode(route.title, "UTF-8")
        val decodedContent = URLDecoder.decode(route.content, "UTF-8")
        val decodedRef = route.quranReference?.let { URLDecoder.decode(it, "UTF-8") }
        // Passed through as-is, empty included — see navigateToDuaDetail. This mirrored the
        // same `127 + duaNumber` guess, so fixing only the sending side changed nothing:
        // the route arrived empty and was re-fabricated here.
        val newsResourceId = route.newsResourceId

        DuaDetailScreen(
            title = decodedTitle,
            content = decodedContent,
            quranReference = decodedRef,
            onBackClick = onBackClick,
            currentDuaIndex = route.duaNumber - 1,
            onNavigateToDua = null, // Navigation handled via back stack for now
            onNavigateToSurah = onNavigateToSurah,
            initialNewsResourceId = newsResourceId,
            isNiaBookmarked = isBookmarked,
            onToggleNiaBookmark = onToggleBookmark,
            topicId = route.topicId,
            onTopicClick = onTopicClick,
            onHadithClick = onHadithClick
        )
    }
}
