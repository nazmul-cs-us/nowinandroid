package com.starception.submission.feature.hadith

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import com.starception.submission.feature.surah.QuranFonts

internal const val HADITH_ARABIC_FONT_PREFS = "quran_prefs"
internal const val HADITH_ARABIC_FONT_KEY = "arabic_font"
internal const val DEFAULT_HADITH_ARABIC_FONT = "pdms_saleem"

internal val hadithArabicFonts = listOf(
    "pdms_saleem",
    "noor_e_hidayat",
    "thabit",
    "uthmani_script",
    "indopak_script",
    "amiri",
    "scheherazade",
)

internal fun hadithArabicFontName(font: String): String = when (font) {
    "pdms_saleem" -> "PDMS Saleem"
    "noor_e_hidayat" -> "Noor e Hidayat"
    "thabit" -> "Thabit"
    "uthmani_script" -> "Uthmani Script"
    "indopak_script" -> "IndoPak Script"
    "amiri" -> "Amiri"
    "scheherazade" -> "Scheherazade"
    else -> "PDMS Saleem"
}

internal fun hadithArabicFontFamily(font: String): FontFamily = when (font) {
    "noor_e_hidayat" -> QuranFonts.NoorEHidayat
    "thabit" -> QuranFonts.Thabit
    "uthmani_script" -> QuranFonts.UthmanicScript
    "indopak_script" -> QuranFonts.IndoPakScript
    "amiri" -> QuranFonts.Amiri
    "scheherazade" -> QuranFonts.Scheherazade
    else -> QuranFonts.PDMSSaleem
}

@Composable
internal fun rememberHadithArabicFont(context: Context): State<String> {
    val preferences = remember(context) {
        context.getSharedPreferences(HADITH_ARABIC_FONT_PREFS, Context.MODE_PRIVATE)
    }
    val selected = remember(preferences) {
        mutableStateOf(
            preferences.getString(HADITH_ARABIC_FONT_KEY, DEFAULT_HADITH_ARABIC_FONT)
                ?: DEFAULT_HADITH_ARABIC_FONT,
        )
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == HADITH_ARABIC_FONT_KEY) {
                selected.value = prefs.getString(key, DEFAULT_HADITH_ARABIC_FONT)
                    ?: DEFAULT_HADITH_ARABIC_FONT
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return selected
}

internal fun saveHadithArabicFont(context: Context, font: String) {
    context.getSharedPreferences(HADITH_ARABIC_FONT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(HADITH_ARABIC_FONT_KEY, font)
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HadithReadingSettingsSheet(
    selectedFont: String,
    selectedVoice: String,
    onFontClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = "Hadith settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Personalize Arabic reading and narration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    HadithSettingRow(
                        title = "Arabic font",
                        value = hadithArabicFontName(selectedFont),
                        onClick = onFontClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    HadithSettingRow(
                        title = "Narration voice",
                        value = selectedVoice,
                        onClick = onVoiceClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HadithArabicFontSheet(
    selectedFont: String,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = "Arabic font",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        text = "Choose the script used for Hadith Arabic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(hadithArabicFonts, key = { it }) { font ->
                            val isSelected = font == selectedFont
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .background(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable { onFontSelected(font) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hadithArabicFontName(font),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                        fontFamily = hadithArabicFontFamily(font),
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "Selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
