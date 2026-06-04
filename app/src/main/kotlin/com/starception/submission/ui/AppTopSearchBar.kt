package com.starception.submission.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.starception.submission.R
import com.starception.submission.core.data.model.RecentSearchQuery
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.feature.search.SuggestedVerse
import com.starception.submission.feature.search.SuggestedVerses
import com.starception.submission.feature.search.VoiceSearchService
import com.starception.submission.feature.search.WhisperVoiceService
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

@Composable
fun AppTopSearchBar(
    title: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    onVerseClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onSearchSubmit: (query: String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val pillBackground = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val topInsetPx = with(LocalDensity.current) { topInset.roundToPx() }
    val currentContent by rememberUpdatedState(content)
    val viewModel = hiltViewModel<TopBarSearchViewModel>()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val currentOnVerseClick by rememberUpdatedState(onVerseClick)
    val currentOnSearchSubmit by rememberUpdatedState(onSearchSubmit)

    val context = LocalContext.current
    // Track the live query so renderSuggestions can filter in real time as the
    // user types. mutableStateOf survives recomposition; the TextWatcher writes
    // here and Compose re-invokes update() so the suggestion list re-renders.
    var liveQuery by remember { mutableStateOf("") }
    val whisperService = remember(context) { WhisperVoiceService(context.applicationContext) }
    val cloudVoiceService = remember(context) { VoiceSearchService(context.applicationContext) }
    var isListening by remember { mutableStateOf(false) }

    DisposableEffect(whisperService) {
        whisperService.initialize()
        onDispose { whisperService.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val themed = ContextThemeWrapper(
                ctx,
                MaterialR.style.Theme_Material3Expressive_DayNight_NoActionBar,
            )
            val root = LayoutInflater.from(themed)
                .inflate(R.layout.app_top_search_bar, null, false) as ViewGroup

            val searchBar = root.findViewById<SearchBar>(R.id.app_search_bar)
            val searchView = root.findViewById<SearchView>(R.id.app_search_view)
            val appBar = root.findViewById<AppBarLayout>(R.id.app_bar_layout)
            val leading = root.findViewById<MaterialButton>(R.id.leading_button)
            val settings = root.findViewById<MaterialButton>(R.id.settings_button)
            val contentContainer = root.findViewById<FrameLayout>(R.id.content_container)

            root.background = ColorDrawable(Color.TRANSPARENT)
            appBar.background = ColorDrawable(Color.TRANSPARENT)
            appBar.backgroundTintList = null
            searchBar.backgroundTintList = ColorStateList.valueOf(pillBackground)

            val hintText = ctx.getString(R.string.app_top_bar_search_hint) + " " + title
            searchBar.hint = hintText
            searchView.hint = hintText
            searchBar.inflateMenu(R.menu.app_top_search_bar_menu)
            searchBar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    searchView.show()
                    startVoiceCapture(
                        ctx = ctx,
                        searchBar = searchBar,
                        searchView = searchView,
                        whisper = whisperService,
                        cloud = cloudVoiceService,
                        onListeningChanged = { isListening = it },
                        onSearchSubmit = { q -> currentOnSearchSubmit(q) },
                        viewModel = viewModel,
                    )
                } else {
                    searchView.show()
                }
                true
            }
            searchView.inflateMenu(R.menu.app_top_search_bar_menu)
            searchView.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    startVoiceCapture(
                        ctx = ctx,
                        searchBar = searchBar,
                        searchView = searchView,
                        whisper = whisperService,
                        cloud = cloudVoiceService,
                        onListeningChanged = { isListening = it },
                        onSearchSubmit = { q -> currentOnSearchSubmit(q) },
                        viewModel = viewModel,
                    )
                }
                true
            }

            leading.setOnClickListener { searchView.show() }
            settings.setOnClickListener { onSettingsClick() }

            searchView.setupWithSearchBar(searchBar)
            searchView.getEditText().addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    liveQuery = s?.toString().orEmpty()
                }
            })
            searchView.getEditText().setOnEditorActionListener { _, _, _ ->
                val query = searchView.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.saveSearchQuery(query)
                    searchBar.setText(query)
                    searchView.hide()
                    currentOnSearchSubmit(query)
                }
                false
            }

            val composeView = ComposeView(ctx).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                tag = COMPOSE_CONTENT_TAG
            }
            contentContainer.addView(
                composeView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            root
        },
        update = { root ->
            val searchBar = root.findViewById<SearchBar>(R.id.app_search_bar)
            val appBar = root.findViewById<AppBarLayout>(R.id.app_bar_layout)
            val contentContainer = root.findViewById<FrameLayout>(R.id.content_container)
            val composeView = contentContainer.findViewWithTag<ComposeView>(COMPOSE_CONTENT_TAG)
            val suggestionContainer = root.findViewById<LinearLayout>(R.id.search_suggestion_container)

            val searchView = root.findViewById<SearchView>(R.id.app_search_view)
            val hintText = root.context.getString(R.string.app_top_bar_search_hint) + " " + title
            searchBar.hint = hintText
            searchView.hint = hintText
            appBar.setPadding(0, topInsetPx, 0, 0)

            renderSuggestions(
                container = suggestionContainer,
                searchBar = searchBar,
                searchView = searchView,
                recentSearches = recentSearches,
                query = liveQuery,
                onVerseClick = { surah, ayah ->
                    searchView.hide()
                    currentOnVerseClick(surah, ayah)
                },
                onRecentClick = { query ->
                    searchBar.setText(query)
                    searchView.hide()
                    currentOnSearchSubmit(query)
                },
            )

            composeView?.setContent {
                NiaTheme { currentContent() }
            }
        },
    )
}

private const val COMPOSE_CONTENT_TAG = "app_top_search_bar_compose_content"
private const val SUGGESTION_STATE_TAG = "app_top_search_bar_suggestion_state"

/**
 * Re-renders the SearchView suggestion list. When [query] is blank we show
 * three sections: YESTERDAY (today + yesterday's queries), THIS WEEK
 * (2–7 days ago), POPULAR VERSES. When the user types, recents and verses are
 * filtered by [query] so matches surface in real time without leaving the view.
 */
private fun renderSuggestions(
    container: LinearLayout,
    searchBar: SearchBar,
    searchView: SearchView,
    recentSearches: List<RecentSearchQuery>,
    query: String,
    onVerseClick: (Int, Int) -> Unit,
    onRecentClick: (String) -> Unit,
) {
    val trimmedQuery = query.trim()
    val isFiltering = trimmedQuery.isNotEmpty()

    val (yesterdayQueries, thisWeekQueries) = partitionRecentSearches(recentSearches)
    val filteredYesterday = if (isFiltering) {
        yesterdayQueries.filter { it.query.contains(trimmedQuery, ignoreCase = true) }
    } else yesterdayQueries
    val filteredThisWeek = if (isFiltering) {
        thisWeekQueries.filter { it.query.contains(trimmedQuery, ignoreCase = true) }
    } else thisWeekQueries
    val filteredVerses = if (isFiltering) {
        SuggestedVerses.search(trimmedQuery)
    } else SuggestedVerses.verses

    val stateKey = buildString {
        append(trimmedQuery)
        append("##")
        append(filteredYesterday.joinToString("|") { it.query })
        append("##")
        append(filteredThisWeek.joinToString("|") { it.query })
        append("##")
        append(filteredVerses.joinToString("|") { "${it.surahNumber}:${it.ayahNumber}" })
    }
    if (container.getTag(SUGGESTION_STATE_TAG.hashCode()) == stateKey) return
    container.setTag(SUGGESTION_STATE_TAG.hashCode(), stateKey)

    container.removeAllViews()
    val ctx = container.context
    val inflater = LayoutInflater.from(ctx)

    if (filteredYesterday.isNotEmpty()) {
        addSectionTitle(container, inflater, ctx.getString(R.string.app_search_section_yesterday))
        filteredYesterday.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, onRecentClick)
        }
    }
    if (filteredThisWeek.isNotEmpty()) {
        addSectionTitle(container, inflater, ctx.getString(R.string.app_search_section_this_week))
        filteredThisWeek.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, onRecentClick)
        }
    }
    if (filteredVerses.isNotEmpty()) {
        addSectionTitle(container, inflater, ctx.getString(R.string.app_search_section_popular_verses))
        filteredVerses.forEach { verse ->
            addVerseItem(container, inflater, verse, onVerseClick)
        }
    }
}

/**
 * Splits recent searches into (yesterday-or-today, 2–7-days-ago). Anything older
 * than a week is dropped — old searches stop being useful as quick-suggestions.
 */
private fun partitionRecentSearches(
    recents: List<RecentSearchQuery>,
): Pair<List<RecentSearchQuery>, List<RecentSearchQuery>> {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val yesterdayCutoff = today.minus(1, DateTimeUnit.DAY)
    val weekCutoff = today.minus(7, DateTimeUnit.DAY)

    val yesterday = mutableListOf<RecentSearchQuery>()
    val thisWeek = mutableListOf<RecentSearchQuery>()
    recents.forEach { recent ->
        val date = recent.queriedDate.toLocalDateTime(tz).date
        when {
            date >= yesterdayCutoff -> yesterday.add(recent)
            date >= weekCutoff -> thisWeek.add(recent)
        }
    }
    return yesterday to thisWeek
}

private fun addSectionTitle(parent: ViewGroup, inflater: LayoutInflater, text: String) {
    val view = inflater.inflate(R.layout.app_search_suggestion_title, parent, false) as TextView
    view.text = text
    parent.addView(view)
}

private fun addRecentSearchItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    query: String,
    onClick: (String) -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon)
        .setImageResource(R.drawable.ic_app_search_schedule_24)
    view.findViewById<TextView>(R.id.app_search_suggestion_title).text = query
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).visibility = View.GONE
    view.setOnClickListener { onClick(query) }
    parent.addView(view)
}

private fun addVerseItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    verse: SuggestedVerse,
    onClick: (Int, Int) -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_verse_item, parent, false)
    view.findViewById<TextView>(R.id.app_search_verse_badge).text =
        "${verse.surahNumber}:${verse.ayahNumber}"
    view.findViewById<TextView>(R.id.app_search_verse_title).text = verse.name
    view.findViewById<TextView>(R.id.app_search_verse_arabic).text = verse.arabicName
    view.findViewById<TextView>(R.id.app_search_verse_subtitle).text = verse.description
    view.findViewById<TextView>(R.id.app_search_verse_category).text = verse.category
    view.setOnClickListener { onClick(verse.surahNumber, verse.ayahNumber) }
    parent.addView(view)
}

/**
 * Mic tap: try offline Whisper first (private, low-latency), fall back to the
 * cloud SpeechRecognizer if Whisper isn't initialised. Result is written into
 * the SearchView's edit field and then submitted via [onSearchSubmit] so the
 * caller can navigate to full FTS results.
 */
private fun startVoiceCapture(
    ctx: Context,
    searchBar: SearchBar,
    searchView: SearchView,
    whisper: WhisperVoiceService,
    cloud: VoiceSearchService,
    onListeningChanged: (Boolean) -> Unit,
    onSearchSubmit: (String) -> Unit,
    viewModel: TopBarSearchViewModel,
) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Toast.makeText(ctx, "Microphone permission required", Toast.LENGTH_SHORT).show()
        return
    }

    val handleResult: (VoiceSearchService.VoiceSearchResult) -> Unit = { result ->
        onListeningChanged(false)
        when (result) {
            is VoiceSearchService.VoiceSearchResult.Success -> {
                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    // Drop transcription into the inline field; the TextWatcher
                    // then refreshes filtered suggestions. User picks a verse
                    // or hits Enter — voice itself never auto-navigates.
                    searchView.setText(text)
                }
            }
            is VoiceSearchService.VoiceSearchResult.Error -> {
                Toast.makeText(ctx, result.message, Toast.LENGTH_SHORT).show()
            }
            VoiceSearchService.VoiceSearchResult.Cancelled -> Unit
        }
    }

    onListeningChanged(true)
    if (whisper.isInitialized.value) {
        whisper.startListening(handleResult)
    } else {
        cloud.startListening(handleResult)
    }
}
