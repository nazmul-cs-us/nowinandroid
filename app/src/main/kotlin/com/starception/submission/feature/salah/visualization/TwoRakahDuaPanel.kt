package com.starception.submission.feature.salah.visualization

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.icon.topicIconResFor
import com.starception.submission.core.ui.ChapterAudioController
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.MissingContentCard
import com.starception.submission.feature.dua.getArabicFontFamilyForDua
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The prayer words paired with the currently animated phase.
 *
 * Fortress chapters contain alternatives, not a checklist to recite end-to-end. This panel
 * therefore exposes every invocation while making it clear that the learner selects one.
 */
@Composable
fun TwoRakahDuaPanel(
    state: VisualizationState,
    catalog: TwoRakahDuaCatalog,
    modifier: Modifier = Modifier,
    onPauseSample: () -> Unit = {},
    isVoiceEngineAvailable: Boolean = true,
    voiceDownloadCategory: String? = null,
    voiceResourceName: String = "Offline Voice",
    downloadManager: AssetDownloadManager? = null,
    onVoiceDownloadComplete: () -> Unit = {},
    glassBackdrop: Backdrop? = null,
) {
    if (state.posePlaybackSource != PosePlaybackSource.TWO_RAKAH_SAMPLE) return

    val step = state.currentTwoRakahStep()
    val chapterId = step.fortressChapterId
    val duas = chapterId?.let(catalog.chapters::get).orEmpty()
    var selectedIndex by rememberSaveable(chapterId) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (duas.size - 1).coerceAtLeast(0))
    val selectedDua = duas.getOrNull(safeIndex)
    val audioUrl = selectedDua?.audioUrl
    val isThisAudioPlaying = audioUrl != null &&
        ChapterAudioController.currentUrl == audioUrl && ChapterAudioController.isPlaying
    val isThisAudioLoading = audioUrl != null && ChapterAudioController.loadingUrl == audioUrl
    var automaticAudioUrl by remember { mutableStateOf<String?>(null) }
    val latestAutomaticAudioUrl by rememberUpdatedState(automaticAudioUrl)
    val context = LocalContext.current
    val selectedFont = remember(context) {
        context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
            .getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
    }
    val arabicFont = remember(selectedFont) { getArabicFontFamilyForDua(selectedFont) }
    val density = LocalDensity.current
    val panelShape = RoundedCornerShape(20.dp)
    val isGlass = glassBackdrop != null
    val accentColor = if (isGlass) Color(0xFF62E2C2) else MaterialTheme.colorScheme.primary
    val primaryContentColor = if (isGlass) {
        Color(0xFFF1F7F4)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryContentColor = if (isGlass) {
        Color(0xFFD2E0DA)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val mutedContentColor = if (isGlass) {
        Color(0xFFA9BAB3)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val glassSurfaceColor = Color(0xFF111A1B).copy(alpha = 0.34f)

    fun playSelectedDuaFromStart() {
        val dua = selectedDua ?: return
        val url = dua.audioUrl ?: return
        val playbackTitle = "${dua.chapterTitle}: Dua ${dua.position}"
        ChapterAudioController.currentTitle = playbackTitle
        ChapterAudioController.currentTopic = "Fortress of the Muslim"
        ChapterAudioController.playlistTitles = listOf(playbackTitle)
        // The pose label is already visible in the simulator. Start the authentic Arabic
        // invocation directly; a generated English track announcement would delay it and make
        // the recitation appear to belong to the following movement.
        ChapterAudioController.playFromStart(url, announceTitle = false)
    }

    // Starting the prayer sample now starts the authentic recording for its current phase.
    // The step index is part of the key so repeated phases (such as both sujud) replay the
    // clip from the beginning instead of toggling the previous playback off.
    LaunchedEffect(
        state.isTwoRakahPlaying,
        state.sampleRakahCount,
        state.twoRakahStepIndex,
        audioUrl,
    ) {
        val previousUrl = automaticAudioUrl
        if (!state.isTwoRakahPlaying || audioUrl == null) {
            previousUrl?.let(ChapterAudioController::stop)
            automaticAudioUrl = null
            return@LaunchedEffect
        }
        if (previousUrl != null && previousUrl != audioUrl) {
            ChapterAudioController.stop(previousUrl)
        }
        automaticAudioUrl = audioUrl
        playSelectedDuaFromStart()
    }

    DisposableEffect(Unit) {
        onDispose {
            latestAutomaticAudioUrl?.let(ChapterAudioController::stop)
        }
    }

    val panelModifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .then(
                if (glassBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { panelShape },
                        effects = {
                            vibrancy()
                            blur(with(density) { 8.dp.toPx() })
                            lens(
                                with(density) { 6.dp.toPx() },
                                with(density) { 12.dp.toPx() },
                            )
                        },
                        onDrawSurface = { drawRect(glassSurfaceColor) },
                    )
                } else {
                    Modifier
                },
            )

    Surface(
        modifier = panelModifier,
        shape = panelShape,
        color = if (glassBackdrop == null) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            if (isGlass) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        contentColor = primaryContentColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isVoiceEngineAvailable && voiceDownloadCategory != null && downloadManager != null) {
                key(voiceDownloadCategory) {
                    MissingContentCard(
                        resourceName = "$voiceResourceName Voice",
                        category = voiceDownloadCategory,
                        description = "Download the offline voice package for spoken prayer-step labels. The recorded dua can still play without it.",
                        downloadManager = downloadManager,
                        onDownloadComplete = onVoiceDownloadComplete,
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (isGlass) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(
                                checkNotNull(topicIconResFor("Prayer")),
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RAK'AH ${step.rakah} · ${step.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = selectedDua?.chapterTitle
                            ?: if (chapterId != null) "Fortress of the Muslim" else "Prayer guidance",
                        style = MaterialTheme.typography.titleSmall,
                        color = primaryContentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (audioUrl != null) {
                    IconButton(
                        onClick = {
                            onPauseSample()
                            if (isThisAudioPlaying || isThisAudioLoading) {
                                ChapterAudioController.stop(audioUrl)
                            } else {
                                playSelectedDuaFromStart()
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (isThisAudioLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(21.dp),
                                color = accentColor,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (isThisAudioPlaying) {
                                    NiaIcons.Pause
                                } else {
                                    NiaIcons.VolumeUp
                                },
                                contentDescription = if (isThisAudioPlaying) {
                                    "Pause dua recitation"
                                } else {
                                    "Play dua recitation"
                                },
                                tint = accentColor,
                            )
                        }
                    }
                }
                if (duas.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isGlass) {
                            Color.White.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            text = "${safeIndex + 1} / ${duas.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryContentColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            when {
                chapterId == null -> {
                    Text(
                        text = step.guidance.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContentColor,
                    )
                    Text(
                        text = "This movement has no dedicated Fortress chapter.",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedContentColor,
                    )
                }

                catalog.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = accentColor,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Loading Fortress supplications…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryContentColor,
                        )
                    }
                }

                selectedDua == null -> {
                    Text(
                        text = catalog.errorMessage ?: "No supplication was found for this phase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isGlass) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    if (duas.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Choose one authentic alternative",
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryContentColor,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    onPauseSample()
                                    selectedIndex = (safeIndex - 1 + duas.size) % duas.size
                                },
                                enabled = !isThisAudioLoading && !isThisAudioPlaying,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = NiaIcons.ChevronLeft,
                                    contentDescription = "Previous supplication",
                                )
                            }
                            IconButton(
                                onClick = {
                                    onPauseSample()
                                    selectedIndex = (safeIndex + 1) % duas.size
                                },
                                enabled = !isThisAudioLoading && !isThisAudioPlaying,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = NiaIcons.ChevronRight,
                                    contentDescription = "Next supplication",
                                )
                            }
                        }
                    }

                    selectedDua.instruction?.takeIf(String::isNotBlank)?.let { instruction ->
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isGlass) Color(0xFFFFD58A) else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            selectedDua.arabic?.takeIf(String::isNotBlank)?.let { arabic ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isGlass) {
                                        Color.Black.copy(alpha = 0.20f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    },
                                ) {
                                    Text(
                                        text = arabic,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = arabicFont,
                                            fontSize = 26.sp,
                                            lineHeight = 40.sp,
                                            textAlign = TextAlign.End,
                                            textDirection = TextDirection.Rtl,
                                        ),
                                        color = primaryContentColor,
                                    )
                                }
                            }
                            selectedDua.transliteration?.takeIf(String::isNotBlank)?.let { transliteration ->
                                Text(
                                    text = transliteration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = primaryContentColor,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                            selectedDua.translation?.takeIf(String::isNotBlank)?.let { translation ->
                                Text(
                                    text = translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryContentColor,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Fortress of the Muslim · Chapter $chapterId",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedContentColor,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Dua ${selectedDua.position}",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedContentColor,
                        )
                    }
                }
            }
        }
    }
}
