package com.starception.submission.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Carries the home surface contrast across AppTopSearchBar's ComposeView boundary. */
internal object HomeSystemBarAppearance {
    private val mutableAppearance = MutableStateFlow(HomeSystemBarState())
    val appearance = mutableAppearance.asStateFlow()

    fun update(lightIcons: Boolean?, bannerLightIcons: Boolean? = null) {
        mutableAppearance.value = HomeSystemBarState(lightIcons, bannerLightIcons)
    }
}

internal data class HomeSystemBarState(
    val lightIcons: Boolean? = null,
    val bannerLightIcons: Boolean? = null,
)

internal fun useDarkStatusBarIcons(
    darkTheme: Boolean,
    homePortrait: Boolean,
    homeLightIcons: Boolean?,
    searchOpen: Boolean,
    insightImmersive: Boolean,
    homeBannerLightIcons: Boolean? = null,
): Boolean = when {
    // A held/pulled status strip sits above both the artwork and the search sheet.
    homePortrait && homeBannerLightIcons != null -> !homeBannerLightIcons
    searchOpen -> !darkTheme
    insightImmersive -> false
    homePortrait -> !(homeLightIcons ?: true)
    else -> !darkTheme
}
