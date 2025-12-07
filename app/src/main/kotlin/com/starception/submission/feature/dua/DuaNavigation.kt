package com.starception.submission.feature.dua

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Route for Dua detail screen
 * @param title The title of the dua
 * @param content The content of the dua (URL encoded)
 * @param quranReference Optional Quran reference (e.g., "2:127")
 * @param duaNumber The dua number (1-40 for Rabbana duas)
 */
@Serializable
data class DuaDetailRoute(
    val title: String,
    val content: String,
    val quranReference: String? = null,
    val duaNumber: Int = 1
)

/**
 * Navigate to Dua detail screen
 */
fun NavController.navigateToDuaDetail(
    title: String,
    content: String,
    quranReference: String? = null,
    duaNumber: Int = 1,
    navOptions: NavOptions? = null
) {
    val encodedContent = URLEncoder.encode(content, "UTF-8")
    val encodedTitle = URLEncoder.encode(title, "UTF-8")
    val encodedRef = quranReference?.let { URLEncoder.encode(it, "UTF-8") }
    navigate(
        route = DuaDetailRoute(
            title = encodedTitle,
            content = encodedContent,
            quranReference = encodedRef,
            duaNumber = duaNumber
        ),
        navOptions = navOptions
    )
}

/**
 * Add Dua detail screen to navigation graph
 */
fun NavGraphBuilder.duaDetailScreen(
    onBackClick: () -> Unit,
    onNavigateToSurah: ((surahNumber: Int, ayahNumber: Int) -> Unit)? = null
) {
    composable<DuaDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<DuaDetailRoute>()
        val decodedTitle = URLDecoder.decode(route.title, "UTF-8")
        val decodedContent = URLDecoder.decode(route.content, "UTF-8")
        val decodedRef = route.quranReference?.let { URLDecoder.decode(it, "UTF-8") }

        DuaDetailScreen(
            title = decodedTitle,
            content = decodedContent,
            quranReference = decodedRef,
            onBackClick = onBackClick,
            currentDuaIndex = route.duaNumber - 1,
            onNavigateToDua = null, // Navigation handled via back stack for now
            onNavigateToSurah = onNavigateToSurah
        )
    }
}
