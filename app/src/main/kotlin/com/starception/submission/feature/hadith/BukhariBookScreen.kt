package com.starception.submission.feature.hadith

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithEntity
import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.ui.FlaticonBookIcon
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.core.ui.FlaticonPlayIcon
import com.starception.submission.core.ui.FlaticonSearchIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BUKHARI_DATABASE = "sahih_bukhari.db"

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
    val selectedArabicFont by rememberHadithArabicFont(context)
    val arabicFontFamily = remember(selectedArabicFont) {
        hadithArabicFontFamily(selectedArabicFont)
    }
    val book = remember(bookId) { BukhariBooks.find(bookId) }
    val loadState by produceState<BukhariBookLoadState>(
        initialValue = BukhariBookLoadState.Loading,
        key1 = book,
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
                title = { Text(book?.nameEnglish ?: "Sahih Bukhari") },
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
                BukhariBookMessage(
                    title = "Download Sahih Bukhari first",
                    message = "Return to the Sahih Bukhari Interest and download the collection to browse this book.",
                    modifier = Modifier.padding(innerPadding),
                )
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "book-header") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlaticonBookIcon(
                            contentDescription = "Bukhari book",
                            iconSize = 32.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Book ${book.id} of 97",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = book.nameEnglish,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = book.nameArabic,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = arabicFontFamily,
                            lineHeight = 34.sp,
                        ),
                        textAlign = TextAlign.End,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${book.hadithCount} hadiths · ${book.firstHadithId}–${book.lastHadithId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                        FilledTonalButton(onClick = onPlayAllClick) {
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    FlaticonSearchIcon(contentDescription = null)
                },
                placeholder = { Text("Search within this book") },
                supportingText = {
                    Text("${filteredHadiths.size} of ${hadiths.size} hadiths")
                },
            )
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NiaTopicTag(
                followed = true,
                onClick = onClick,
                text = { Text("Hadith ${hadith.id}") },
            )
            hadith.textPlain?.takeIf(String::isNotBlank)?.let { english ->
                Text(
                    text = english.replace(Regex("\\s+"), " ").trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = hadith.textArabic.replace(Regex("\\s+"), " ").trim(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = arabicFontFamily,
                    lineHeight = 34.sp,
                ),
                textAlign = TextAlign.End,
                maxLines = 4,
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
        FlaticonBookIcon(
            contentDescription = null,
            iconSize = 64.dp,
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
