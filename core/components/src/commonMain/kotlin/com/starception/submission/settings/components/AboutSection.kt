package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private const val PRIVACY_POLICY_URL = "https://policies.google.com/privacy"
private const val BRAND_GUIDELINES_URL = "https://developer.android.com/distribute/marketing-tools/brand-guidelines"
private const val FEEDBACK_URL = "https://goo.gle/nia-app-feedback"
private const val FLATICON_URL = "https://www.flaticon.com/uicons"
private const val OPEN_METEO_URL = "https://open-meteo.com/"
private const val METEOCONS_URL = "https://meteocons.com/"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSection(
    modifier: Modifier = Modifier,
    versionName: String = "1.0.0",
    showLicenses: Boolean = true,
    showProjectLinks: Boolean = true,
    /**
     * Opens the open-source licences screen. Android launches Google's
     * OssLicensesMenuActivity, which is an Activity and cannot cross. Platforms
     * without an implementation hide the action with [showLicenses].
     */
    onOpenLicenses: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier) {
        // App Version
        Text(
            text = "Version",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = versionName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Attributions
        Text(
            text = "Attributions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Interface, location, and weather PNG icons by Flaticon creators. " +
                "Animated weather icons by Meteocons. Weather data by Open-Meteo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Links
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.Start
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (showProjectLinks) {
                TextButton(
                    onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
                ) {
                    Text(text = "Privacy Policy")
                }
            }

            if (showLicenses) {
                TextButton(
                    onClick = {
                        onOpenLicenses()
                    }
                ) {
                    Text(text = "Licenses")
                }
            }

            if (showProjectLinks) {
                TextButton(
                    onClick = { uriHandler.openUri(BRAND_GUIDELINES_URL) }
                ) {
                    Text(text = "Brand Guidelines")
                }

                TextButton(
                    onClick = { uriHandler.openUri(FEEDBACK_URL) }
                ) {
                    Text(text = "Feedback")
                }
            }

            TextButton(
                onClick = { uriHandler.openUri(FLATICON_URL) }
            ) {
                Text(text = "UIcons by Flaticon")
            }

            TextButton(
                onClick = { uriHandler.openUri(OPEN_METEO_URL) }
            ) {
                Text(text = "Weather by Open-Meteo")
            }

            TextButton(
                onClick = { uriHandler.openUri(METEOCONS_URL) }
            ) {
                Text(text = "Animations by Meteocons")
            }
        }
    }
}
