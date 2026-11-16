package com.starception.submission.settings.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAppGuideStore(): AppGuideStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val preferences = context.getSharedPreferences("deenly_app_guide", Context.MODE_PRIVATE)
        object : AppGuideStore {
            override fun visited(section: GuideSection) = preferences.getBoolean("visited_" + section.name, false)
            override fun visit(section: GuideSection) { preferences.edit().putBoolean("visited_" + section.name, true).apply() }
            override fun tipSeen(section: GuideSection) = preferences.getBoolean("tip_seen_" + section.name, false)
            override fun markTipSeen(section: GuideSection) { preferences.edit().putBoolean("tip_seen_" + section.name, true).apply() }
            override fun tipsEnabled() = preferences.getBoolean("tips_enabled", true)
            override fun setTipsEnabled(enabled: Boolean) { preferences.edit().putBoolean("tips_enabled", enabled).apply() }
        }
    }
}
