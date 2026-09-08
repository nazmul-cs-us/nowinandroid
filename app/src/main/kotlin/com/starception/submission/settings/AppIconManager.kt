package com.starception.submission.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import com.starception.submission.R

enum class AppIconChoice(
    val displayName: String,
    @param:DrawableRes val previewDrawableRes: Int,
    internal val aliasClassName: String,
) {
    EMERALD(
        displayName = "Emerald",
        previewDrawableRes = R.drawable.ic_launcher_emerald_foreground,
        aliasClassName = "com.starception.submission.launcher.EmeraldLauncherAlias",
    ),
    ROYAL(
        displayName = "Royal",
        previewDrawableRes = R.drawable.ic_launcher_foreground_art,
        aliasClassName = "com.starception.submission.launcher.RoyalLauncherAlias",
    ),
    NEON(
        displayName = "Neon",
        previewDrawableRes = R.drawable.ic_launcher_neon_foreground,
        aliasClassName = "com.starception.submission.launcher.NeonLauncherAlias",
    ),
    HORIZON(
        displayName = "Horizon",
        previewDrawableRes = R.drawable.ic_launcher_horizon_foreground,
        aliasClassName = "com.starception.submission.launcher.HorizonLauncherAlias",
    ),
}

object AppIconManager {
    private const val PREFERENCES_NAME = "app_icon_preferences"
    private const val SELECTED_ICON_KEY = "selected_icon"

    fun selectedIcon(context: Context): AppIconChoice {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val storedChoice = preferences.getString(SELECTED_ICON_KEY, null)
            ?.let { storedName -> AppIconChoice.entries.firstOrNull { it.name == storedName } }

        if (storedChoice != null && !isExplicitlyDisabled(context, storedChoice)) {
            return storedChoice
        }

        return AppIconChoice.entries.firstOrNull { choice ->
            context.packageManager.getComponentEnabledSetting(choice.componentName(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIconChoice.ROYAL
    }

    fun selectIcon(context: Context, choice: AppIconChoice) {
        if (selectedIcon(context) == choice) return

        val packageManager = context.packageManager
        val flags = PackageManager.DONT_KILL_APP

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = AppIconChoice.entries.map { candidate ->
                PackageManager.ComponentEnabledSetting(
                    candidate.componentName(context),
                    if (candidate == choice) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    flags,
                )
            }
            packageManager.setComponentEnabledSettings(settings)
        } else {
            // Enable the replacement before disabling the current alias so the app
            // always keeps one launcher entry on Android 12 and earlier.
            packageManager.setComponentEnabledSetting(
                choice.componentName(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                flags,
            )
            AppIconChoice.entries
                .filterNot { it == choice }
                .forEach { candidate ->
                    packageManager.setComponentEnabledSetting(
                        candidate.componentName(context),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        flags,
                    )
                }
        }

        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SELECTED_ICON_KEY, choice.name)
            .apply()
    }

    private fun isExplicitlyDisabled(context: Context, choice: AppIconChoice): Boolean =
        context.packageManager.getComponentEnabledSetting(choice.componentName(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun AppIconChoice.componentName(context: Context): ComponentName =
        ComponentName(context.packageName, aliasClassName)
}
