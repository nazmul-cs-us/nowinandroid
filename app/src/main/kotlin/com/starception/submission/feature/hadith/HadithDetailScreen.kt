package com.starception.submission.feature.hadith

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.hadithdatabase.Hadith
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.translation.TranslationService
import com.starception.submission.feature.surah.QuranFonts
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.ui.DynamicSkyHeader
import com.starception.submission.core.ui.ImmersiveFullScreenEffect
import com.starception.submission.core.ui.getCurrentSkyPeriodForTheme
import com.starception.submission.core.ui.getSkyColors
import java.util.Locale

// Gradient colors for hadith header - earthy tones
private val HadithGradientColors = listOf(
    Color(0xFF795548),  // Brown
    Color(0xFF8D6E63),  // Light brown
    Color(0xFF6D4C41),  // Dark brown
    Color(0xFF5D4037),  // Darker brown
    Color(0xFF4E342E)   // Deepest brown
)

/**
 * Hadith Detail Screen - displays a single hadith from a collection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithDetailScreen(
    collectionName: String,
    hadithNumber: Int,
    databaseFile: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Enable immersive full-screen mode (hides status bar)
    ImmersiveFullScreenEffect()

    val context = LocalContext.current
    val repository = remember { HadithRepository.getInstance(context) }
    val translationService = remember { TranslationService.getInstance(context) }

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hadith by remember { mutableStateOf<Hadith?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Translation state
    var translatedArabic by remember { mutableStateOf<String?>(null) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var translatedElaboration by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(translationService.getSelectedLanguage()) }
    var selectedProvider by remember { mutableStateOf(translationService.getSelectedProvider()) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Available translations
    val availableTranslations = listOf("en", "ar", "bn", "zh", "es", "fr", "id", "ru", "sv", "tr", "ur")

    // Available providers
    val availableProviders = listOf(
        "auto" to "Auto (Reverso → Google)",
        "google" to "Google Translate",
        "reverso" to "Reverso"
    )

    // Load hadith
    LaunchedEffect(databaseFile, hadithNumber) {
        isLoading = true
        error = null
        translatedText = null
        translatedElaboration = null
        try {
            hadith = repository.getHadith(databaseFile, hadithNumber)
            if (hadith == null) {
                error = "Hadith not found"
            }
        } catch (e: Exception) {
            error = e.message ?: "Error loading hadith"
            android.util.Log.e("HadithDetailScreen", "Error loading hadith", e)
        }
        isLoading = false
    }

    // Translate content when hadith is loaded or settings change
    // The translation service handles all cases including Arabic → English
    LaunchedEffect(hadith, selectedLanguage, selectedProvider) {
        val currentHadith = hadith ?: return@LaunchedEffect

        // Skip translation only for transliteration (no API support)
        if (selectedLanguage == "transliteration") {
            translatedArabic = null  // Show original Arabic
            translatedText = currentHadith.textPlain
            translatedElaboration = currentHadith.elaboration
            return@LaunchedEffect
        }

        // If Arabic is selected, show original Arabic text without translation
        if (selectedLanguage == "ar") {
            translatedArabic = null  // Show original Arabic
            translatedText = currentHadith.textPlain
            translatedElaboration = currentHadith.elaboration
            return@LaunchedEffect
        }

        isTranslating = true
        try {
            // Translate Arabic hadith text (main hadith)
            if (!currentHadith.textArabic.isNullOrEmpty()) {
                translatedArabic = translationService.translate(currentHadith.textArabic!!, selectedLanguage)
                android.util.Log.d("HadithDetailScreen", "Translated textArabic to $selectedLanguage")
            } else {
                translatedArabic = null
            }

            // Translate textPlain (Translation section)
            if (!currentHadith.textPlain.isNullOrEmpty()) {
                translatedText = translationService.translate(currentHadith.textPlain!!, selectedLanguage)
                android.util.Log.d("HadithDetailScreen", "Translated textPlain to $selectedLanguage")
            } else {
                translatedText = null
            }

            // Translate elaboration (Explanation section)
            if (!currentHadith.elaboration.isNullOrEmpty()) {
                translatedElaboration = translationService.translate(currentHadith.elaboration!!, selectedLanguage)
                android.util.Log.d("HadithDetailScreen", "Translated elaboration to $selectedLanguage")
            } else {
                translatedElaboration = null
            }
        } catch (e: Exception) {
            android.util.Log.e("HadithDetailScreen", "Translation error", e)
            // Fall back to original text
            translatedArabic = null
            translatedText = currentHadith.textPlain
            translatedElaboration = currentHadith.elaboration
        }
        isTranslating = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent  // Transparent to let sky extend to top
    ) { _ ->
        // Don't apply paddingValues - let content scroll under transparent toolbar like SurahDetailScreen
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    HadithShimmerLoading(onBackClick = onBackClick, isLandscape = isLandscape)
                }
                error != null -> {
                    HadithErrorContent(
                        error = error!!,
                        onBackClick = onBackClick
                    )
                }
                hadith != null -> {
                    HadithContent(
                        hadith = hadith!!,
                        collectionName = collectionName,
                        hadithNumber = hadithNumber,
                        onBackClick = onBackClick,
                        translatedArabic = translatedArabic,
                        translatedText = translatedText,
                        translatedElaboration = translatedElaboration,
                        isTranslating = isTranslating,
                        selectedLanguage = selectedLanguage,
                        onLanguageClick = { showLanguageDialog = true },
                        isLandscape = isLandscape
                    )
                }
            }
        }
    }

    // Language selection dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Translation Settings") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 450.dp)
                ) {
                    // Provider section
                    item {
                        Text(
                            text = "Translation Provider",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(availableProviders) { (providerCode, providerName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProvider = providerCode
                                    translationService.setSelectedProvider(providerCode)
                                    // Clear cache to force re-translation with new provider
                                    translationService.clearCache()
                                    translatedText = null
                                    translatedElaboration = null
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = providerCode == selectedProvider,
                                onClick = {
                                    selectedProvider = providerCode
                                    translationService.setSelectedProvider(providerCode)
                                    translationService.clearCache()
                                    translatedText = null
                                    translatedElaboration = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = providerName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Divider
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Language section
                    item {
                        Text(
                            text = "Target Language",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(availableTranslations) { langCode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (langCode != selectedLanguage) {
                                        // Clear cache to force re-translation
                                        translationService.clearCache()
                                        translatedText = null
                                        translatedElaboration = null
                                        selectedLanguage = langCode
                                    }
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = langCode == selectedLanguage,
                                onClick = {
                                    if (langCode != selectedLanguage) {
                                        translationService.clearCache()
                                        translatedText = null
                                        translatedElaboration = null
                                        selectedLanguage = langCode
                                    }
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getLanguageName(langCode),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun HadithContent(
    hadith: Hadith,
    collectionName: String,
    hadithNumber: Int,
    onBackClick: () -> Unit,
    translatedArabic: String? = null,
    translatedText: String? = null,
    translatedElaboration: String? = null,
    isTranslating: Boolean = false,
    selectedLanguage: String = "en",
    onLanguageClick: () -> Unit = {},
    isLandscape: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // No status bar padding - immersive mode hides status bar
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with dynamic sky
            item {
                val skyPeriod = getCurrentSkyPeriodForTheme()
                val headerHeight = if (isLandscape) 200.dp else 420.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                ) {
                    // Dynamic sky background based on time of day
                    DynamicSkyHeader(
                        modifier = Modifier.fillMaxSize(),
                        height = headerHeight,
                        period = skyPeriod
                    )
                    // Semi-transparent overlay for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                    // Header content positioned at bottom to show more sky artwork
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isLandscape) 24.dp else 16.dp)
                            .padding(top = if (isLandscape) 60.dp else 100.dp, bottom = 4.dp), // Position content at very bottom
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Collection name
                        Text(
                            text = hadith.collectionNameEnglish.ifEmpty { collectionName },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Hadith number - using NiaTopicTag for consistency with Dua detail
                        NiaTopicTag(
                            followed = false,
                            onClick = { },
                            enabled = true,
                            text = {
                                Text(
                                    text = "Hadith #$hadithNumber".uppercase(Locale.getDefault())
                                )
                            }
                        )

                        // Author
                        if (hadith.author.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Compiled by ${hadith.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Arabic Text Card with optional translation
            if (hadith.textArabic.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Original Arabic text
                            Text(
                                text = hadith.textArabic,
                                fontFamily = QuranFonts.PDMSSaleem,
                                fontSize = 26.sp,
                                lineHeight = 48.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Show translated Arabic text when available
                            if (translatedArabic != null && selectedLanguage != "ar") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Language label
                                Text(
                                    text = "Hadith (${getLanguageName(selectedLanguage)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Translated text
                                Text(
                                    text = translatedArabic,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 26.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (isTranslating && selectedLanguage != "ar") {
                                // Loading indicator while translating
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    repeat(2) { index ->
                                        val widthFraction = if (index == 0) 0.9f else 0.7f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(widthFraction)
                                                .height(14.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Translation section - shows translated text based on selected language
            if (!hadith.textPlain.isNullOrEmpty()) {
                item {
                    val displayText = translatedText ?: hadith.textPlain!!
                    val sectionTitle = if (selectedLanguage != "en" && selectedLanguage != "transliteration") {
                        "Translation (${getLanguageName(selectedLanguage)})"
                    } else {
                        "Translation"
                    }
                    HadithSectionCard(
                        title = sectionTitle,
                        accentColor = MaterialTheme.colorScheme.primary,
                        content = displayText,
                        isLoading = isTranslating && translatedText == null
                    )
                }
            }

            // Elaboration/Explanation section - shows translated elaboration
            if (!hadith.elaboration.isNullOrEmpty()) {
                item {
                    val displayText = translatedElaboration ?: hadith.elaboration!!
                    val sectionTitle = if (selectedLanguage != "en" && selectedLanguage != "transliteration") {
                        "Explanation (${getLanguageName(selectedLanguage)})"
                    } else {
                        "Explanation"
                    }
                    HadithSectionCard(
                        title = sectionTitle,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        content = displayText,
                        isExpanded = false,
                        isLoading = isTranslating && translatedElaboration == null
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Fixed toolbar at top - transparent to show sky through
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding() // Account for status bar/punch hole
                .offset(y = (-8).dp) // Move toolbar higher
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Language selector button
                IconButton(onClick = onLanguageClick) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = "Select Translation Language",
                        tint = Color.White
                    )
                }

                // Collection badge - using NiaTopicTag for consistency with Dua detail
                NiaTopicTag(
                    followed = false,
                    onClick = { },
                    enabled = true,
                    text = {
                        Text(
                            text = collectionName.uppercase(Locale.getDefault())
                        )
                    }
                )

                // More options menu
                IconButton(onClick = { /* TODO: More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Get display name for a language code
 */
private fun getLanguageName(code: String): String {
    return when (code) {
        "ar" -> "Arabic"
        "bn" -> "Bengali"
        "zh" -> "Chinese"
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "id" -> "Indonesian"
        "ru" -> "Russian"
        "sv" -> "Swedish"
        "tr" -> "Turkish"
        "ur" -> "Urdu"
        else -> code.uppercase()
    }
}

@Composable
private fun HadithSectionCard(
    title: String,
    accentColor: Color,
    content: String,
    isExpanded: Boolean = true,
    isLoading: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(androidx.compose.ui.unit.Dp.Unspecified)
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Simple loading indicator
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    // Shimmer loading effect for text
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(3) { index ->
                            val widthFraction = when (index) {
                                0 -> 1f
                                1 -> 0.85f
                                else -> 0.6f
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(widthFraction)
                                    .height(14.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                } else {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithErrorContent(
    error: String,
    onBackClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error loading hadith",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}

@Composable
private fun HadithShimmerLoading(
    onBackClick: () -> Unit,
    isLandscape: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val skyPeriod = getCurrentSkyPeriodForTheme()
    val skyColors = getSkyColors(skyPeriod)
    val headerHeight = if (isLandscape) 200.dp else 420.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header shimmer with dynamic sky
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight) // Match taller header
            ) {
                // Dynamic sky background based on time of day
                DynamicSkyHeader(
                    modifier = Modifier.fillMaxSize(),
                    height = headerHeight,
                    period = skyPeriod
                )
                // Semi-transparent overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                // Header content positioned at bottom to show more sky artwork
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isLandscape) 24.dp else 16.dp)
                        .padding(top = if (isLandscape) 60.dp else 100.dp, bottom = 4.dp), // Position content at very bottom
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Collection name placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 150.dp, height = 24.dp)
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Hadith number placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 36.dp)
                            .background(
                                Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(18.dp)
                            )
                    )
                }
            }

            // Content shimmer
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                }
            }
        }

        // Back button toolbar - transparent to show sky through
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // Account for status bar/punch hole
                .offset(y = (-8).dp) // Move toolbar higher
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
