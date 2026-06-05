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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
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
    // Use primaryContainer so the SearchBar pill takes on the user's selected
    // brand color (e.g. Royal -> pale Lapis blue) instead of the near-cream
    // surfaceVariant that's visually indistinguishable from the page background.
    val pillBackground = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val pillTextColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    // Inflated SearchView ships M3 default lavender — repaint with brand colors.
    val searchViewBg = MaterialTheme.colorScheme.surface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val titleColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
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
    // SearchBar's bounds inside the AndroidView, used to position the
    // Gemini-style listening glow overlay precisely around the pill.
    var searchBarBoundsPx by remember { mutableStateOf<Rect?>(null) }

    DisposableEffect(whisperService) {
        whisperService.initialize()
        onDispose { whisperService.release() }
    }

    Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
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

            // Track the SearchBar's position so the listening glow overlay can
            // be drawn precisely around the pill. Bounds are reported relative
            // to the inflated CoordinatorLayout (= the AndroidView's own coord
            // space), which matches the overlay Canvas's coordinate space.
            searchBar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val loc = IntArray(2)
                val rootLoc = IntArray(2)
                v.getLocationInWindow(loc)
                root.getLocationInWindow(rootLoc)
                val l = (loc[0] - rootLoc[0]).toFloat()
                val t = (loc[1] - rootLoc[1]).toFloat()
                searchBarBoundsPx = Rect(l, t, l + v.width.toFloat(), t + v.height.toFloat())
            }

            val hintText = ctx.getString(R.string.app_top_bar_search_hint) + " " + title
            searchBar.hint = hintText
            searchBar.textView?.setHintTextColor(pillTextColor)
            searchBar.textView?.setTextColor(pillTextColor)
            searchView.hint = hintText
            searchBar.inflateMenu(R.menu.app_top_search_bar_menu)
            searchBar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    // Mic tap on the collapsed SearchBar starts voice capture in-place —
                    // do NOT expand the SearchView. Transcribed text is submitted via
                    // currentOnSearchSubmit so the user navigates straight to results.
                    startVoiceCapture(
                        ctx = ctx,
                        searchBar = searchBar,
                        searchView = searchView,
                        whisper = whisperService,
                        cloud = cloudVoiceService,
                        onListeningChanged = { isListening = it },
                        onSearchSubmit = { q -> currentOnSearchSubmit(q) },
                        viewModel = viewModel,
                        autoSubmit = true,
                    )
                } else {
                    searchView.show()
                }
                true
            }
            searchView.inflateMenu(R.menu.app_top_search_bar_menu)
            searchView.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    // Mic tap inside the expanded SearchView fills the input field
                    // but does NOT auto-submit — the user can still edit before searching.
                    startVoiceCapture(
                        ctx = ctx,
                        searchBar = searchBar,
                        searchView = searchView,
                        whisper = whisperService,
                        cloud = cloudVoiceService,
                        onListeningChanged = { isListening = it },
                        onSearchSubmit = { q -> currentOnSearchSubmit(q) },
                        viewModel = viewModel,
                        autoSubmit = false,
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
                // setParentCompositionContext is intentionally NOT used here —
                // it propagates CompositionLocals but loses the inner content's
                // ability to participate in layout/draw, leaving the body blank.
                // Re-wrap in NiaTheme inside setContent instead so the user's
                // selected brand still reaches the inner subtree.
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
            searchBar.textView?.setHintTextColor(pillTextColor)
            searchBar.textView?.setTextColor(pillTextColor)
            searchView.hint = hintText
            appBar.setPadding(0, topInsetPx, 0, 0)

            // Re-tint only the SearchView's INTERNAL surface (open_search_view_background)
            // and the toolbar container — those become visible when expanded.
            // Do NOT call searchView.setBackgroundColor: SearchView itself is sized
            // match_parent x match_parent and that paints over the body even when collapsed.
            root.findViewById<View?>(
                MaterialR.id.open_search_view_background
            )?.setBackgroundColor(searchViewBg)
            root.findViewById<View?>(
                MaterialR.id.open_search_view_toolbar_container
            )?.setBackgroundColor(searchViewBg)
            searchView.getEditText().setTextColor(titleColor)

            renderSuggestions(
                container = suggestionContainer,
                searchBar = searchBar,
                searchView = searchView,
                recentSearches = recentSearches,
                query = liveQuery,
                accentColor = accentColor,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
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
    // Gemini-style listening glow: a multi-color gradient that flows around
    // the SearchBar pill while the mic is capturing. The overlay sits above
    // the AndroidView so the SearchBar stays interactive when not listening.
    val bounds = searchBarBoundsPx
    if (isListening && bounds != null) {
        ListeningEdgeGlow(bounds = bounds, modifier = Modifier.matchParentSize())
    }
    }
}

@Composable
private fun ListeningEdgeGlow(
    bounds: Rect,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "listenGlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

        val strokeWidth = 3.dp.toPx()
        val cornerRadius = bounds.height / 2f
        val width = bounds.width
        val shift = phase * width

        val colors = listOf(
            androidx.compose.ui.graphics.Color(0xFF4285F4), // Google blue
            androidx.compose.ui.graphics.Color(0xFFEA4335), // red
            androidx.compose.ui.graphics.Color(0xFFFBBC04), // yellow
            androidx.compose.ui.graphics.Color(0xFF34A853), // green
            androidx.compose.ui.graphics.Color(0xFF4285F4), // wrap
        )
        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(bounds.left - width + shift, bounds.center.y),
            end = Offset(bounds.left + shift, bounds.center.y),
            tileMode = TileMode.Repeated,
        )

        // Outer soft halo — wider, fainter, pulses with `pulse`.
        val haloInset = strokeWidth * 2f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(bounds.left - haloInset, bounds.top - haloInset),
            size = Size(bounds.width + haloInset * 2f, bounds.height + haloInset * 2f),
            cornerRadius = CornerRadius(cornerRadius + haloInset),
            style = Stroke(width = haloInset * 2f),
            alpha = pulse * 0.25f,
        )

        // Crisp main stroke hugging the pill.
        drawRoundRect(
            brush = brush,
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = strokeWidth),
            alpha = 0.55f + 0.45f * pulse,
        )
    }
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
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
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
        addSectionTitle(container, inflater, ctx.getString(R.string.app_search_section_yesterday), subtitleColor)
        filteredYesterday.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, titleColor, subtitleColor, onRecentClick)
        }
    }
    if (filteredThisWeek.isNotEmpty()) {
        addSectionTitle(container, inflater, ctx.getString(R.string.app_search_section_this_week), subtitleColor)
        filteredThisWeek.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, titleColor, subtitleColor, onRecentClick)
        }
    }
    if (filteredVerses.isNotEmpty()) {
        addSectionTitle(container, inflater, ctx.getString(R.string.app_search_section_popular_verses), subtitleColor)
        filteredVerses.forEach { verse ->
            addVerseItem(container, inflater, verse, accentColor, titleColor, subtitleColor, onVerseClick)
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

private fun addSectionTitle(parent: ViewGroup, inflater: LayoutInflater, text: String, subtitleColor: Int) {
    val view = inflater.inflate(R.layout.app_search_suggestion_title, parent, false) as TextView
    view.text = text
    view.setTextColor(subtitleColor)
    parent.addView(view)
}

private fun addRecentSearchItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    query: String,
    titleColor: Int,
    subtitleColor: Int,
    onClick: (String) -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_schedule_24)
        imageTintList = android.content.res.ColorStateList.valueOf(subtitleColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = query
        setTextColor(titleColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).visibility = View.GONE
    view.setOnClickListener { onClick(query) }
    parent.addView(view)
}

private fun addVerseItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    verse: SuggestedVerse,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: (Int, Int) -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_verse_item, parent, false)
    view.findViewById<TextView>(R.id.app_search_verse_badge).apply {
        text = "${verse.surahNumber}:${verse.ayahNumber}"
        setTextColor(accentColor)
    }
    view.findViewById<TextView>(R.id.app_search_verse_title).apply {
        text = verse.name
        setTextColor(titleColor)
    }
    view.findViewById<TextView>(R.id.app_search_verse_arabic).apply {
        text = verse.arabicName
        setTextColor(subtitleColor)
    }
    view.findViewById<TextView>(R.id.app_search_verse_subtitle).apply {
        text = verse.description
        setTextColor(subtitleColor)
    }
    view.findViewById<TextView>(R.id.app_search_verse_category).apply {
        text = verse.category
        setTextColor(accentColor)
    }
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
    autoSubmit: Boolean,
) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Toast.makeText(ctx, "Microphone permission required", Toast.LENGTH_SHORT).show()
        return
    }

    Toast.makeText(ctx, "Listening…", Toast.LENGTH_SHORT).show()

    val handleResult: (VoiceSearchService.VoiceSearchResult) -> Unit = { result ->
        onListeningChanged(false)
        when (result) {
            is VoiceSearchService.VoiceSearchResult.Success -> {
                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    if (autoSubmit) {
                        // Mic tap from collapsed bar: skip the SearchView entirely and
                        // navigate straight to results.
                        viewModel.saveSearchQuery(text)
                        searchBar.setText(text)
                        onSearchSubmit(text)
                    } else {
                        // Mic tap from inside expanded SearchView: just fill the field
                        // so the user can review and submit (or pick a suggestion).
                        searchView.setText(text)
                    }
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
