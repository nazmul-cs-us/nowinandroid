package com.starception.submission.feature.surah.tajweed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog showing the Tajweed color legend with all rules and their meanings.
 */
@Composable
fun TajweedLegendDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tajweed Color Guide",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Learn the meaning of each color",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Legend items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(TajweedRule.entries.toList()) { rule ->
                        TajweedLegendItem(rule = rule)
                    }
                }

                // Close button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Got it!")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TajweedLegendItem(rule: TajweedRule) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rule.color),
                contentAlignment = Alignment.Center
            ) {
                // Show a sample Arabic letter
                Text(
                    text = getSampleLetter(rule),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }

            // Rule info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Arabic name
                Text(
                    text = rule.arabicName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // English name and description
                Text(
                    text = getEnglishName(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Brief description
                Text(
                    text = getRuleDescription(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun getSampleLetter(rule: TajweedRule): String {
    return when (rule) {
        TajweedRule.GHUNNAH -> "نّ"
        TajweedRule.HAMZAT_WASL -> "ٱ"
        TajweedRule.IDGHAAM_GHUNNAH -> "نم"
        TajweedRule.IDGHAAM_NO_GHUNNAH -> "نل"
        TajweedRule.IDGHAAM_MUTAJANISAYN -> "تط"
        TajweedRule.IDGHAAM_MUTAQARIBAYN -> "قك"
        TajweedRule.IDGHAAM_SHAFAWI -> "مب"
        TajweedRule.IKHFA -> "نت"
        TajweedRule.IKHFA_SHAFAWI -> "مب"
        TajweedRule.IQLAB -> "نب"
        TajweedRule.LAM_SHAMSIYYAH -> "الش"
        TajweedRule.MADD_2 -> "ا"
        TajweedRule.MADD_6 -> "آ"
        TajweedRule.MADD_246 -> "ـا"
        TajweedRule.MADD_MUTTASIL -> "ءا"
        TajweedRule.MADD_MUNFASIL -> "ا ء"
        TajweedRule.QALQALAH -> "قطب"
        TajweedRule.SILENT -> "ـ"
    }
}

private fun getEnglishName(rule: TajweedRule): String {
    return when (rule) {
        TajweedRule.GHUNNAH -> "Ghunnah (Nasalization)"
        TajweedRule.HAMZAT_WASL -> "Hamzat al-Wasl (Connecting Hamza)"
        TajweedRule.IDGHAAM_GHUNNAH -> "Idghaam with Ghunnah"
        TajweedRule.IDGHAAM_NO_GHUNNAH -> "Idghaam without Ghunnah"
        TajweedRule.IDGHAAM_MUTAJANISAYN -> "Idghaam Mutajanisayn"
        TajweedRule.IDGHAAM_MUTAQARIBAYN -> "Idghaam Mutaqaribayn"
        TajweedRule.IDGHAAM_SHAFAWI -> "Idghaam Shafawi (Labial)"
        TajweedRule.IKHFA -> "Ikhfa (Concealment)"
        TajweedRule.IKHFA_SHAFAWI -> "Ikhfa Shafawi (Labial Concealment)"
        TajweedRule.IQLAB -> "Iqlab (Conversion)"
        TajweedRule.LAM_SHAMSIYYAH -> "Lam Shamsiyyah (Sun Letter)"
        TajweedRule.MADD_2 -> "Madd (2 counts)"
        TajweedRule.MADD_6 -> "Madd Lazim (6 counts)"
        TajweedRule.MADD_246 -> "Madd (2-4-6 counts)"
        TajweedRule.MADD_MUTTASIL -> "Madd Muttasil (Connected)"
        TajweedRule.MADD_MUNFASIL -> "Madd Munfasil (Separated)"
        TajweedRule.QALQALAH -> "Qalqalah (Echoing)"
        TajweedRule.SILENT -> "Silent Letter"
    }
}

private fun getRuleDescription(rule: TajweedRule): String {
    return when (rule) {
        TajweedRule.GHUNNAH -> "Hold the sound in the nose for 2 counts"
        TajweedRule.HAMZAT_WASL -> "Silent when connecting, pronounced when starting"
        TajweedRule.IDGHAAM_GHUNNAH -> "Merge with nasalization (ي ن م و)"
        TajweedRule.IDGHAAM_NO_GHUNNAH -> "Merge completely without nasalization (ل ر)"
        TajweedRule.IDGHAAM_MUTAJANISAYN -> "Merge letters from same articulation point"
        TajweedRule.IDGHAAM_MUTAQARIBAYN -> "Merge letters from close articulation points"
        TajweedRule.IDGHAAM_SHAFAWI -> "Merge Meem into Meem with nasalization"
        TajweedRule.IKHFA -> "Hide the noon/tanween before certain letters"
        TajweedRule.IKHFA_SHAFAWI -> "Hide the Meem before Ba with light nasalization"
        TajweedRule.IQLAB -> "Convert noon/tanween to Meem before Ba"
        TajweedRule.LAM_SHAMSIYYAH -> "Lam is silent, following letter is doubled"
        TajweedRule.MADD_2 -> "Extend the vowel for 2 counts"
        TajweedRule.MADD_6 -> "Extend the vowel for 6 counts (obligatory)"
        TajweedRule.MADD_246 -> "Extend for 2, 4, or 6 counts based on context"
        TajweedRule.MADD_MUTTASIL -> "Extend 4-5 counts when hamza follows in same word"
        TajweedRule.MADD_MUNFASIL -> "Extend 2-4-5 counts when hamza is in next word"
        TajweedRule.QALQALAH -> "Echo the sound of ق ط ب ج د"
        TajweedRule.SILENT -> "Letter is written but not pronounced"
    }
}
