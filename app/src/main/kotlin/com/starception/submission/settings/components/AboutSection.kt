package com.starception.submission.settings.components

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

private const val PRIVACY_POLICY_URL = "https://policies.google.com/privacy"
private const val BRAND_GUIDELINES_URL = "https://developer.android.com/distribute/marketing-tools/brand-guidelines"
private const val FEEDBACK_URL = "https://goo.gle/nia-app-feedback"
private const val FLATICON_URL = "https://www.flaticon.com/uicons"
private const val OPEN_METEO_URL = "https://open-meteo.com/"
private const val METEOCONS_URL = "https://meteocons.com/"
private const val VOICE_ANIMATION_URL =
    "https://www.figma.com/community/file/1487058393232924456/apple-face-id-lottie-animation"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSection(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Column(modifier = modifier) {
        // App Version
        Text(
            text = "Version",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "1.0.0",
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
                "Animated weather icons by Meteocons. Weather data by Open-Meteo. " +
                "Voice-search motion adapted from Mau Ali's Apple Face ID Lottie Animation.",
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
            TextButton(
                onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
            ) {
                Text(text = "Privacy Policy")
            }

            TextButton(
                onClick = {
                    context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                }
            ) {
                Text(text = "Licenses")
            }

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

            TextButton(
                onClick = { uriHandler.openUri(VOICE_ANIMATION_URL) }
            ) {
                Text(text = "Voice animation credit")
            }
        }
    }
}
