package com.starception.submission.usersettings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.prayer.repository.PrayerSettingsRepository.CountrySwitchProposal

/**
 * Consent bottom sheet shown when the app detects the user has moved to a new country. Prayer
 * settings are NOT changed unless the user taps Apply/Restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySwitchConsentSheet(
    proposal: CountrySwitchProposal,
    onApply: () -> Unit,
    onKeepCurrent: () -> Unit,
    onDismissForNow: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val place = proposal.countryName ?: proposal.countryCode

    // Swiping the sheet away is NOT a decision — it reappears on the next app open. Only the
    // "Keep current" button is a terminal decline.
    ModalBottomSheet(onDismissRequest = onDismissForNow, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "You're now in $place",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (proposal.isRestore) {
                    "Restore your saved prayer settings for $place?"
                } else {
                    "Update your prayer calculation to $place's method?"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    LabeledValue(label = "Calculation method", value = proposal.proposedMethod)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    LabeledValue(label = "Asr calculation", value = proposal.proposedAsr)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Currently using ${proposal.currentMethod}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NiaOutlinedButton(
                    onClick = onKeepCurrent,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text("Keep current")
                }
                NiaOutlinedButton(
                    onClick = onApply,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(if (proposal.isRestore) "Restore" else "Apply")
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
