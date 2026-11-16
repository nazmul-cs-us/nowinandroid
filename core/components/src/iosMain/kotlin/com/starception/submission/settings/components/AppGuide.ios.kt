package com.starception.submission.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberAppGuideStore(): AppGuideStore = remember {
    val defaults = NSUserDefaults.standardUserDefaults
    object : AppGuideStore {
        override fun visited(section: GuideSection) = defaults.boolForKey("deenly_guide_visited_" + section.name)
        override fun visit(section: GuideSection) { defaults.setBool(true, forKey = "deenly_guide_visited_" + section.name) }
        override fun tipSeen(section: GuideSection) = defaults.boolForKey("deenly_guide_tip_seen_" + section.name)
        override fun markTipSeen(section: GuideSection) { defaults.setBool(true, forKey = "deenly_guide_tip_seen_" + section.name) }
        override fun tipsEnabled() = defaults.objectForKey("deenly_guide_tips_enabled") == null || defaults.boolForKey("deenly_guide_tips_enabled")
        override fun setTipsEnabled(enabled: Boolean) { defaults.setBool(enabled, forKey = "deenly_guide_tips_enabled") }
    }
}
