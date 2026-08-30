package com.starception.submission.feature.hadith

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.icon.topicIconResFor
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithEntity
import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.core.ui.FlaticonPlayIcon
import com.starception.submission.core.ui.FlaticonSearchIcon
import com.starception.submission.download.MissingContentCard
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BUKHARI_DATABASE = "sahih_bukhari.db"
private const val BUKHARI_DOWNLOAD_CATEGORY = "hadith_sahih_bukhari"

private sealed interface BukhariBookLoadState {
    data object Loading : BukhariBookLoadState
    data object MissingDatabase : BukhariBookLoadState
    data class Loaded(val hadiths: List<HadithEntity>) : BukhariBookLoadState
    data class Error(val message: String) : BukhariBookLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BukhariBookScreen(
    bookId: Int,
    onBackClick: () -> Unit,
    onHadithClick: (Int) -> Unit,
    onPlayAllClick: () -> Unit,
) {
    val context = LocalContext.current
    val downloadManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SherpaOnnxTtsEntryPoint::class.java,
        ).assetDownloadManager()
    }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val selectedArabicFont by rememberHadithArabicFont(context)
    val arabicFontFamily = remember(selectedArabicFont) {
        hadithArabicFontFamily(selectedArabicFont)
    }
    val book = remember(bookId) { BukhariBooks.find(bookId) }
    val loadState by produceState<BukhariBookLoadState>(
        initialValue = BukhariBookLoadState.Loading,
        key1 = book,
        key2 = reloadTrigger,
    ) {
        value = if (book == null) {
            BukhariBookLoadState.Error("This Bukhari book could not be found.")
        } else if (!HadithDatabase.isDatabaseAvailable(context, BUKHARI_DATABASE)) {
            BukhariBookLoadState.MissingDatabase
        } else {
            try {
                val hadiths = withContext(Dispatchers.IO) {
                    HadithDatabase.getInstance(context, BUKHARI_DATABASE)
                        .hadithDao()
                        .getHadithsInRange(book.firstHadithId, book.lastHadithId)
                }
                BukhariBookLoadState.Loaded(hadiths)
            } catch (error: Exception) {
                BukhariBookLoadState.Error(error.message ?: "Unable to open Sahih Bukhari.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sahih Bukhari") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.ARROW_BACK,
                            contentDescription = "Back",
                            fontSize = 24.sp,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = loadState) {
            BukhariBookLoadState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            BukhariBookLoadState.MissingDatabase -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    MissingContentCard(
                        resourceName = "Sahih Bukhari Hadith Collection",
                        category = BUKHARI_DOWNLOAD_CATEGORY,
                        description = "Download the reader database to browse and play this Bukhari book.",
                        downloadManager = downloadManager,
                        onDownloadComplete = {
                            // A deleted source can leave an open Room handle behind. Close it and
                            // remove Room's managed copy so the next load opens the new CDN file.
                            HadithDatabase.clearInstance(context, BUKHARI_DATABASE)
                            reloadTrigger++
                        },
                    )
                }
            }

            is BukhariBookLoadState.Error -> {
                BukhariBookMessage(
                    title = "Unable to load this book",
                    message = state.message,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            is BukhariBookLoadState.Loaded -> {
                if (book != null) {
                    BukhariHadithList(
                        book = book,
                        hadiths = state.hadiths,
                        onHadithClick = onHadithClick,
                        onPlayAllClick = onPlayAllClick,
                        arabicFontFamily = arabicFontFamily,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun BukhariHadithList(
    book: BukhariBook,
    hadiths: List<HadithEntity>,
    onHadithClick: (Int) -> Unit,
    onPlayAllClick: () -> Unit,
    arabicFontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredHadiths = remember(hadiths, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            hadiths
        } else {
            hadiths.filter { hadith ->
                hadith.id.toString() == normalizedQuery ||
                    hadith.textPlain.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    hadith.textArabic.contains(normalizedQuery)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "book-header") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomEnd = 28.dp,
                    bottomStart = 10.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(
                                checkNotNull(topicIconResFor("Sahih Bukhari")),
                            ),
                            contentDescription = "Bukhari book",
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "BOOK ${book.id.toString().padStart(2, '0')} / 97",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = book.nameEnglish,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = book.nameArabic,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = arabicFontFamily,
                            lineHeight = 30.sp,
                        ),
                        textAlign = TextAlign.End,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NiaTopicTag(
                            followed = false,
                            onClick = {},
                        ) {
                            Text(
                                text = "${book.hadithCount} HADITHS · ${book.firstHadithId}–${book.lastHadithId}",
                                maxLines = 1,
                            )
                        }
                        FilledTonalButton(
                            onClick = onPlayAllClick,
                            shape = RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomEnd = 8.dp,
                                bottomStart = 20.dp,
                            ),
                        ) {
                            FlaticonPlayIcon(
                                contentDescription = null,
                                iconSize = 20.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Play all")
                        }
                    }
                }
            }
        }

        item(key = "hadith-search") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = {
                        FlaticonSearchIcon(contentDescription = null)
                    },
                    placeholder = { Text("Search hadiths") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
                NiaTopicTag(
                    followed = true,
                    onClick = {},
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "${hadiths.size} HADITHS"
                        } else {
                            "${filteredHadiths.size}/${hadiths.size}"
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        if (filteredHadiths.isEmpty()) {
            item(key = "no-hadith-results") {
                Text(
                    text = "No hadiths match your search.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filteredHadiths, key = HadithEntity::id) { hadith ->
                BukhariHadithCard(
                    hadith = hadith,
                    onClick = { onHadithClick(hadith.id) },
                    arabicFontFamily = arabicFontFamily,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.size(20.dp)) }
    }
}

@Composable
private fun BukhariHadithCard(
    hadith: HadithEntity,
    onClick: () -> Unit,
    arabicFontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 22.dp,
            topEnd = 22.dp,
            bottomEnd = 22.dp,
            bottomStart = 8.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NiaTopicTag(
                followed = true,
                onClick = onClick,
                text = { Text("HADITH ${hadith.id}") },
            )
            hadith.textPlain?.takeIf(String::isNotBlank)?.let { english ->
                Text(
                    text = english.replace(Regex("\\s+"), " ").trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = hadith.textArabic.replace(Regex("\\s+"), " ").trim(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = arabicFontFamily,
                    lineHeight = 30.sp,
                ),
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BukhariBookMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(
                checkNotNull(topicIconResFor("Sahih Bukhari")),
            ),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
