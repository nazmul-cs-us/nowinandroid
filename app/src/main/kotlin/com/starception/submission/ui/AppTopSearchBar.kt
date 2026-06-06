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
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import androidx.core.content.res.ResourcesCompat
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
import com.starception.submission.ui.search.InMemorySearchResult
import com.starception.submission.ui.search.PopularSuggestion
import com.starception.submission.ui.search.SearchHints
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
    // All non-verse search tap callbacks (topic, news, fortress dua, quranic dua,
    // ayah) flow through this CompositionLocal — set once at NavHost level so
    // direct callers like PrayerTimesScreen also get them without per-screen
    // boilerplate.
    val searchNav = LocalSearchNavCallbacks.current
    val onTopicClick = searchNav.onTopicClick
    val onNewsClick = searchNav.onNewsClick
    val onFortressDuaClick = searchNav.onFortressDuaClick
    val onQuranicDuaClick = searchNav.onQuranicDuaClick
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
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val inMemoryResults by viewModel.inMemoryResults.collectAsStateWithLifecycle()
    val ayahResults by viewModel.ayahResults.collectAsStateWithLifecycle()
    val fortressDuaResults by viewModel.fortressDuaResults.collectAsStateWithLifecycle()
    val currentOnVerseClick by rememberUpdatedState(onVerseClick)
    val currentOnSearchSubmit by rememberUpdatedState(onSearchSubmit)
    val currentOnTopicClick by rememberUpdatedState(onTopicClick)
    val currentOnNewsClick by rememberUpdatedState(onNewsClick)
    val currentOnFortressDuaClick by rememberUpdatedState(onFortressDuaClick)
    val currentOnQuranicDuaClick by rememberUpdatedState(onQuranicDuaClick)

    val context = LocalContext.current
    // Track the live query so renderSuggestions can filter in real time as the
    // user types. mutableStateOf survives recomposition; the TextWatcher writes
    // here and Compose re-invokes update() so the suggestion list re-renders.
    var liveQuery by remember { mutableStateOf("") }
    // Typewriter-animated hint cycling through SearchHints.rotatingHintsFor —
    // the SearchBar's pill shows a fresh time-of-day-aware suggestion every few
    // seconds. Paused while the SearchView is open so the animation doesn't
    // fight the user's own typing.
    var isSearchViewOpen by remember { mutableStateOf(false) }
    var animatedHint by remember { mutableStateOf(SearchHints.hintFor()) }
    LaunchedEffect(Unit) {
        var hints = SearchHints.rotatingHintsFor()
        var idx = 0
        while (true) {
            // If the user opens the SearchView, pause cycling so we don't
            // animate inside the editor. Re-pick the time slot's hints on
            // resume — the slot may have ticked over while paused.
            while (isSearchViewOpen) {
                delay(250)
            }
            hints = SearchHints.rotatingHintsFor()
            val target = hints[idx % hints.size]
            for (i in 1..target.length) {
                animatedHint = target.substring(0, i)
                delay(45)
            }
            delay(1_800)
            for (i in target.length - 1 downTo 0) {
                animatedHint = target.substring(0, i)
                delay(22)
            }
            delay(180)
            idx++
        }
    }
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

            // Time-of-day-aware hint set on first inflate. The typewriter loop
            // (LaunchedEffect above) overwrites this through `update` and
            // animates char-by-char, cycling between multiple suggestions
            // every few seconds. Beats a static "Search Home" because it
            // teaches users what's actually searchable.
            searchBar.hint = SearchHints.hintFor()

            // Track SearchView open/close so the typewriter pauses while the
            // user is editing — otherwise the animated hint would compete with
            // the SearchView's own (empty-state) placeholder.
            searchView.addTransitionListener { _, _, newState ->
                isSearchViewOpen = newState == SearchView.TransitionState.SHOWN ||
                    newState == SearchView.TransitionState.SHOWING
            }
            // Match the rest of the app — Roboto Serif (downloadable Google Font),
            // same family Compose uses via NiaTheme. SearchBar/SearchView are View
            // components so they ignore Compose Typography and default to system sans.
            val appTypeface = ResourcesCompat.getFont(ctx, R.font.roboto_serif)
            searchBar.textView?.setHintTextColor(pillTextColor)
            searchBar.textView?.setTextColor(pillTextColor)
            searchBar.textView?.typeface = appTypeface
            searchView.hint = SearchHints.hintFor()
            searchView.getEditText().typeface = appTypeface
            searchBar.inflateMenu(R.menu.app_top_search_bar_menu)
            searchBar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    startVoiceCapture(
                        ctx = ctx,
                        searchView = searchView,
                        whisper = whisperService,
                        cloud = cloudVoiceService,
                        onListeningChanged = { isListening = it },
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
                        searchView = searchView,
                        whisper = whisperService,
                        cloud = cloudVoiceService,
                        onListeningChanged = { isListening = it },
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
                    val q = s?.toString().orEmpty()
                    liveQuery = q
                    viewModel.onSearchQueryChanged(q)
                }
            })
            searchView.getEditText().setOnEditorActionListener { _, _, _ ->
                // Pressing Enter / the keyboard search button used to navigate to
                // the old feature:search results page. Now the SearchView itself
                // renders inline FTS rows for everything (duas, topics, news,
                // ayahs), so submit only saves the query as recent and dismisses
                // the keyboard — the user stays put and taps a row to drill in.
                val query = searchView.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.saveSearchQuery(query)
                    searchBar.setText(query)
                }
                true
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
            // Drive the SearchBar's hint from the typewriter state; the
            // SearchView shows its own static placeholder when expanded.
            searchBar.hint = animatedHint
            searchView.hint = SearchHints.hintFor()
            searchBar.textView?.setHintTextColor(pillTextColor)
            searchBar.textView?.setTextColor(pillTextColor)
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
                searchResults = searchResults,
                inMemoryResults = inMemoryResults,
                ayahResults = ayahResults,
                fortressDuaResults = fortressDuaResults,
                accentColor = accentColor,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                onVerseClick = { surah, ayah ->
                    searchView.hide()
                    currentOnVerseClick(surah, ayah)
                },
                onRecentClick = { query ->
                    // Refill the SearchView so inline FTS re-runs against the
                    // chosen recent query — don't dump the user back on the old
                    // feature:search page.
                    searchView.setText(query)
                },
                onPopularClick = { suggestion ->
                    // Tapping a popular-search chip refills the SearchView with
                    // the chip's query so the full ranked-search pipeline runs
                    // against it (same as picking a recent search).
                    searchView.setText(suggestion.query)
                },
                onTopicClick = { id ->
                    viewModel.saveSearchQuery(liveQuery)
                    searchView.hide()
                    currentOnTopicClick(id)
                },
                onNewsClick = { news ->
                    viewModel.saveSearchQuery(liveQuery)
                    searchView.hide()
                    currentOnNewsClick(news)
                },
                onFortressDuaClick = { dua ->
                    viewModel.saveSearchQuery(liveQuery)
                    searchView.hide()
                    currentOnFortressDuaClick(dua)
                },
                onQuranicDuaClick = { dua ->
                    viewModel.saveSearchQuery(liveQuery)
                    searchView.hide()
                    currentOnQuranicDuaClick(dua)
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

        val strokeWidth = 1.5.dp.toPx()
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

        // Both strokes are drawn with their OUTER edge lined up with the pill's
        // inner edge — i.e. nothing extends beyond the pill bounds at any point.
        fun drawInsetStroke(strokePx: Float, inwardOffset: Float, alpha: Float) {
            val center = inwardOffset + strokePx / 2f
            drawRoundRect(
                brush = brush,
                topLeft = Offset(bounds.left + center, bounds.top + center),
                size = Size(
                    (bounds.width - center * 2f).coerceAtLeast(0f),
                    (bounds.height - center * 2f).coerceAtLeast(0f),
                ),
                cornerRadius = CornerRadius((cornerRadius - center).coerceAtLeast(0f)),
                style = Stroke(width = strokePx),
                alpha = alpha,
            )
        }

        // Soft halo, sits inside the border.
        drawInsetStroke(
            strokePx = 6.dp.toPx(),
            inwardOffset = strokeWidth,
            alpha = pulse * 0.25f,
        )

        // Thin gradient border — outer edge touches the pill edge, fully inside.
        drawInsetStroke(
            strokePx = strokeWidth,
            inwardOffset = 0f,
            alpha = 0.55f + 0.45f * pulse,
        )
    }
}

private const val COMPOSE_CONTENT_TAG = "app_top_search_bar_compose_content"
private const val SUGGESTION_STATE_TAG = "app_top_search_bar_suggestion_state"

/**
 * Re-renders the SearchView suggestion list.
 *
 * When the query is blank: shows popular-search chips (rotating with the time
 * of day), YESTERDAY recents, and THIS WEEK recents.
 *
 * When the user types: renders the ranked sections returned by the in-memory
 * service (surahs, Quranic duas, popular verses) plus SQL hits (ayahs, fortress
 * invocations, FTS topics + news). Sections are ordered by the highest-scoring
 * section first so the strongest match leads the list.
 */
private fun renderSuggestions(
    container: LinearLayout,
    searchBar: SearchBar,
    searchView: SearchView,
    recentSearches: List<RecentSearchQuery>,
    query: String,
    searchResults: com.starception.submission.core.model.data.UserSearchResult,
    inMemoryResults: InMemorySearchResult,
    ayahResults: List<com.starception.submission.core.qurandatabase.AyahEntity>,
    fortressDuaResults: List<com.starception.submission.core.duadatabase.Dua>,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onVerseClick: (Int, Int) -> Unit,
    onRecentClick: (String) -> Unit,
    onPopularClick: (PopularSuggestion) -> Unit,
    onTopicClick: (String) -> Unit,
    onNewsClick: (com.starception.submission.core.model.data.UserNewsResource) -> Unit,
    onFortressDuaClick: (com.starception.submission.core.duadatabase.Dua) -> Unit,
    onQuranicDuaClick: (com.starception.submission.core.quranicduas.QuranicDuaEntity) -> Unit,
) {
    val trimmedQuery = query.trim()
    val isFiltering = trimmedQuery.isNotEmpty()

    val (yesterdayQueries, thisWeekQueries) = partitionRecentSearches(recentSearches)
    // Cap to 4 per section so a chatty recent-search history doesn't push
    // the curated verses below the fold. Older queries are still searchable
    // by re-typing — recents are a shortcut, not an archive.
    val filteredYesterday = (
        if (isFiltering) yesterdayQueries.filter { it.query.contains(trimmedQuery, ignoreCase = true) }
        else yesterdayQueries
        ).take(MAX_RECENTS_PER_SECTION)
    val filteredThisWeek = (
        if (isFiltering) thisWeekQueries.filter { it.query.contains(trimmedQuery, ignoreCase = true) }
        else thisWeekQueries
        ).take(MAX_RECENTS_PER_SECTION)
    val popularSuggestions = if (isFiltering) emptyList() else SearchHints.popularSuggestions()
    // Curated highlight verses (Ayatul Kursi, Al-Fatiha, Ar-Rahman, etc.) shown
    // on the empty state so users can jump straight to the staples without
    // typing. Hidden once they start searching — the ranked verseIndex inside
    // inMemoryResults takes over for filtered matches.
    val emptyStateVerses = if (isFiltering) emptyList()
    else SuggestedVerses.verses.take(MAX_EMPTY_STATE_VERSES)

    // In-memory ranked hits (already sorted by score). Cap per-section so the
    // suggestion list stays scannable.
    val rankedSurahs = inMemoryResults.surahs.take(5)
    val rankedQuranicDuas = inMemoryResults.quranicDuas.take(5)
    val rankedVerses = inMemoryResults.verses.take(5)
    // SQL hits (already capped and sorted at the DAO level)
    val cappedAyahs = ayahResults.take(8)
    val cappedFortressDuas = fortressDuaResults.take(8)
    val ftsTopics = searchResults.topics.take(5)
    val ftsNews = searchResults.newsResources.take(8)

    val stateKey = buildString {
        append(trimmedQuery)
        append("##")
        append(filteredYesterday.joinToString("|") { it.query })
        append("##")
        append(filteredThisWeek.joinToString("|") { it.query })
        append("##")
        append(popularSuggestions.joinToString("|") { it.query })
        append("##")
        append(rankedSurahs.joinToString("|") { "s${it.item.number}@${"%.1f".format(it.score)}" })
        append("##")
        append(rankedQuranicDuas.joinToString("|") { "q${it.item.id}@${"%.1f".format(it.score)}" })
        append("##")
        append(rankedVerses.joinToString("|") { "v${it.item.surahNumber}:${it.item.ayahNumber}@${"%.1f".format(it.score)}" })
        append("##")
        append(cappedAyahs.joinToString("|") { "a${it.surahNumber}:${it.numberInSurah}" })
        append("##")
        append(cappedFortressDuas.joinToString("|") { "f${it.id}" })
        append("##")
        append(ftsTopics.joinToString("|") { it.topic.id })
        append("##")
        append(ftsNews.joinToString("|") { it.id })
    }
    if (container.getTag(SUGGESTION_STATE_TAG.hashCode()) == stateKey) return
    container.setTag(SUGGESTION_STATE_TAG.hashCode(), stateKey)

    container.removeAllViews()
    val ctx = container.context
    val inflater = LayoutInflater.from(ctx)

    // Sections are sorted by their highest-scoring item so the strongest match
    // leads. In-memory ranked sources contribute their actual RankedHit score;
    // SQL sources get a fixed prior reflecting natural source priority
    // (popular verses > surahs > ayahs > fortress > topics > news) so they
    // sort sensibly when nothing in-memory dominates.
    //
    // Plus a "section-intent" boost: if the user's raw query contains a kind
    // word ("surah", "dua", "verse", "ayah"), that section is bumped above
    // unrelated SQL/FTS hits even when its only match is fuzzy. This makes
    // "Surah imran" pin Al-Imran first even though "imran" only fuzzy-matches
    // the stored "Imraan".
    val rawWords = trimmedQuery.lowercase().split(Regex("\\s+")).toSet()
    val surahIntent = "surah" in rawWords || "sura" in rawWords || "soorah" in rawWords
    val duaIntent = "dua" in rawWords || "duas" in rawWords
    val verseIntent = "verse" in rawWords || "ayah" in rawWords || "ayat" in rawWords ||
        "verses" in rawWords
    val intentBoost: (Boolean) -> Double = { if (it) INTENT_BOOST else 0.0 }
    val sections = mutableListOf<RenderableSection>()
    if (rankedVerses.isNotEmpty()) {
        sections.add(
            RenderableSection(
                title = ctx.getString(R.string.app_search_section_popular_verses),
                score = rankedVerses.first().score + intentBoost(verseIntent),
            ) {
                rankedVerses.forEach { hit ->
                    addVerseItem(
                        container, inflater, hit.item,
                        accentColor, titleColor, subtitleColor, onVerseClick,
                    )
                }
            },
        )
    }
    if (rankedSurahs.isNotEmpty()) {
        sections.add(
            RenderableSection(
                title = "Surahs",
                score = rankedSurahs.first().score + intentBoost(surahIntent),
            ) {
                rankedSurahs.forEach { hit ->
                    addSurahItem(
                        container, inflater, hit.item,
                        accentColor, titleColor, subtitleColor,
                    ) { onVerseClick(hit.item.number, 1) }
                }
            },
        )
    }
    if (rankedQuranicDuas.isNotEmpty()) {
        sections.add(
            RenderableSection(
                title = "Quranic Duas",
                score = rankedQuranicDuas.first().score + intentBoost(duaIntent),
            ) {
                rankedQuranicDuas.forEach { hit ->
                    addQuranicDuaItem(
                        container, inflater, hit.item,
                        accentColor, titleColor, subtitleColor,
                    ) { onQuranicDuaClick(hit.item) }
                }
            },
        )
    }
    // SQL-source priors (kept below in-memory exact-match scores but above each other)
    if (cappedAyahs.isNotEmpty()) {
        sections.add(
            RenderableSection(
                title = "Quran",
                score = SQL_AYAH_PRIOR + intentBoost(verseIntent),
            ) {
                cappedAyahs.forEach { ayah ->
                    addAyahItem(
                        container, inflater, ayah,
                        accentColor, titleColor, subtitleColor,
                    ) { onVerseClick(ayah.surahNumber, ayah.numberInSurah) }
                }
            },
        )
    }
    if (cappedFortressDuas.isNotEmpty()) {
        sections.add(
            RenderableSection(
                title = "Fortress of the Muslim",
                score = SQL_FORTRESS_PRIOR + intentBoost(duaIntent),
            ) {
                cappedFortressDuas.forEach { dua ->
                    addFortressDuaItem(
                        container, inflater, dua,
                        accentColor, titleColor, subtitleColor,
                    ) { onFortressDuaClick(dua) }
                }
            },
        )
    }
    if (ftsTopics.isNotEmpty()) {
        sections.add(
            RenderableSection(title = "Topics", score = FTS_TOPICS_PRIOR) {
                ftsTopics.forEach { topic ->
                    addTopicItem(
                        container, inflater, topic.topic.name, topic.topic.shortDescription,
                        accentColor, titleColor, subtitleColor,
                    ) { onTopicClick(topic.topic.id) }
                }
            },
        )
    }
    if (ftsNews.isNotEmpty()) {
        sections.add(
            RenderableSection(title = "Duas & Articles", score = FTS_NEWS_PRIOR) {
                ftsNews.forEach { news ->
                    addNewsItem(
                        container, inflater, news.title, news.content,
                        accentColor, titleColor, subtitleColor,
                    ) { onNewsClick(news) }
                }
            },
        )
    }
    sections
        .sortedByDescending { it.score }
        .forEach { section ->
            addSectionTitle(container, inflater, section.title, subtitleColor)
            section.render()
        }

    // Empty-state extras: popular-search chips, then recents.
    if (!isFiltering && popularSuggestions.isNotEmpty()) {
        addSectionTitle(
            container, inflater,
            ctx.getString(R.string.app_search_section_popular_searches), subtitleColor,
        )
        addPopularChipsRow(
            container, inflater, popularSuggestions,
            accentColor, titleColor, onPopularClick,
        )
    }

    // Recents only help when the user has nothing to act on yet — once any
    // real content section has matches, hide them so the relevant hits aren't
    // pushed below the fold.
    val hasContentHits = sections.isNotEmpty()
    if (!hasContentHits && filteredYesterday.isNotEmpty()) {
        addSectionTitle(
            container, inflater,
            ctx.getString(R.string.app_search_section_yesterday), subtitleColor,
        )
        filteredYesterday.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, titleColor, subtitleColor, onRecentClick)
        }
    }
    if (!hasContentHits && filteredThisWeek.isNotEmpty()) {
        addSectionTitle(
            container, inflater,
            ctx.getString(R.string.app_search_section_this_week), subtitleColor,
        )
        filteredThisWeek.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, titleColor, subtitleColor, onRecentClick)
        }
    }
    // Empty-state highlight verses (Ayatul Kursi, Al-Fatiha, Ar-Rahman, …) so
    // the staples are always one tap away even with an empty recents list.
    if (emptyStateVerses.isNotEmpty()) {
        addSectionTitle(
            container, inflater,
            ctx.getString(R.string.app_search_section_popular_verses), subtitleColor,
        )
        emptyStateVerses.forEach { verse ->
            addVerseItem(
                container, inflater, verse,
                accentColor, titleColor, subtitleColor, onVerseClick,
            )
        }
    }
}

private const val MAX_RECENTS_PER_SECTION = 4
private const val MAX_EMPTY_STATE_VERSES = 8

/** Holds a ranked section's title, lead score, and render closure. */
private data class RenderableSection(
    val title: String,
    val score: Double,
    val render: () -> Unit,
)

// Fixed priors for SQL sources so they sort sensibly when in-memory hits don't
// dominate. Anything above ~20 represents a strong in-memory match; below that,
// these priors keep the natural order: ayahs > fortress > topics > news.
private const val SQL_AYAH_PRIOR = 18.0
private const val SQL_FORTRESS_PRIOR = 16.0
private const val FTS_TOPICS_PRIOR = 14.0
private const val FTS_NEWS_PRIOR = 12.0

// Boost applied when the raw query contains a kind word ("surah", "dua",
// "verse") matching the section. Calibrated to bring a fuzzy-only in-memory
// match (≈8–13) above the SQL priors so user intent ("Surah imran" → Surahs)
// pins reliably without over-promoting incidental matches.
private const val INTENT_BOOST = 25.0

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
    view.typeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    parent.addView(view)
}

private fun addSurahItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    surah: com.starception.submission.core.qurandatabase.SurahEntity,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_home_24)
        imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }
    val name = surah.nameEnglish?.takeIf { it.isNotBlank() }
        ?: surah.nameTranslation?.takeIf { it.isNotBlank() }
        ?: surah.nameArabic
        ?: "Surah ${surah.number}"
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = "Surah ${surah.number} — $name"
        setTextColor(titleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        val subtitle = listOfNotNull(
            surah.nameArabic?.takeIf { it.isNotBlank() },
            surah.revelationType?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        text = subtitle
        setTextColor(subtitleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
    }
    view.setOnClickListener { onClick() }
    parent.addView(view)
}

private fun addAyahItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    ayah: com.starception.submission.core.qurandatabase.AyahEntity,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_home_24)
        imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = "${ayah.surahNumber}:${ayah.numberInSurah}"
        setTextColor(accentColor)
        typeface = appTypeface
        maxLines = 1
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        val snippet = ayah.text.replace(Regex("\\s+"), " ").trim()
        text = snippet
        setTextColor(titleColor)
        typeface = appTypeface
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = if (snippet.isBlank()) View.GONE else View.VISIBLE
    }
    view.setOnClickListener { onClick() }
    parent.addView(view)
}

private fun addQuranicDuaItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    dua: com.starception.submission.core.quranicduas.QuranicDuaEntity,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_home_24)
        imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = "Dua ${dua.duaNumber}: ${dua.title}"
        setTextColor(titleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        val snippet = (dua.translation ?: dua.explanation ?: "")
            .replace(Regex("\\s+"), " ").trim()
        text = snippet
        setTextColor(subtitleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = if (snippet.isBlank()) View.GONE else View.VISIBLE
    }
    view.setOnClickListener { onClick() }
    parent.addView(view)
}

private fun addFortressDuaItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    dua: com.starception.submission.core.duadatabase.Dua,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_home_24)
        imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = dua.chapterTitle.ifBlank { "Dua" }
        setTextColor(titleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        val snippet = (dua.translation ?: dua.transliteration ?: dua.context ?: "")
            .replace(Regex("\\s+"), " ").trim()
        text = snippet
        setTextColor(subtitleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = if (snippet.isBlank()) View.GONE else View.VISIBLE
    }
    view.setOnClickListener { onClick() }
    parent.addView(view)
}

private fun addTopicItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    name: String,
    description: String,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_home_24)
        imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = name
        setTextColor(titleColor)
        typeface = appTypeface
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        text = description
        setTextColor(subtitleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = if (description.isBlank()) View.GONE else View.VISIBLE
    }
    view.setOnClickListener { onClick() }
    parent.addView(view)
}

private fun addNewsItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    title: String,
    snippet: String,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_home_24)
        imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = title
        setTextColor(titleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        // Strip markdown-ish bullets & collapse whitespace so the snippet stays scannable.
        val cleaned = snippet.replace(Regex("[\\n\\r]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        text = cleaned
        setTextColor(subtitleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = if (cleaned.isBlank()) View.GONE else View.VISIBLE
    }
    view.setOnClickListener { onClick() }
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
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(R.drawable.ic_app_search_schedule_24)
        imageTintList = android.content.res.ColorStateList.valueOf(subtitleColor)
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = query
        setTextColor(titleColor)
        typeface = appTypeface
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
    val appTypeface = ResourcesCompat.getFont(parent.context, R.font.roboto_serif)
    view.findViewById<TextView>(R.id.app_search_verse_badge).apply {
        text = "${verse.surahNumber}:${verse.ayahNumber}"
        setTextColor(accentColor)
        typeface = appTypeface
    }
    view.findViewById<TextView>(R.id.app_search_verse_title).apply {
        text = verse.name
        setTextColor(titleColor)
        typeface = appTypeface
    }
    view.findViewById<TextView>(R.id.app_search_verse_arabic).apply {
        text = verse.arabicName
        setTextColor(subtitleColor)
    }
    view.findViewById<TextView>(R.id.app_search_verse_subtitle).apply {
        text = verse.description
        setTextColor(subtitleColor)
        typeface = appTypeface
    }
    view.findViewById<TextView>(R.id.app_search_verse_category).apply {
        text = verse.category
        setTextColor(accentColor)
        typeface = appTypeface
    }
    view.setOnClickListener { onClick(verse.surahNumber, verse.ayahNumber) }
    parent.addView(view)
}

/**
 * Renders the empty-state "Popular Searches" row as horizontally-scrollable
 * tappable chips. Tap = refill the SearchView's text with the chip's [query]
 * so the full ranked-search pipeline runs against it.
 */
private fun addPopularChipsRow(
    parent: ViewGroup,
    inflater: LayoutInflater,
    suggestions: List<PopularSuggestion>,
    accentColor: Int,
    titleColor: Int,
    onClick: (PopularSuggestion) -> Unit,
) {
    val ctx = parent.context
    val appTypeface = ResourcesCompat.getFont(ctx, R.font.roboto_serif)
    val scroll = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            // Match the section title's horizontal padding so chips align with
            // the section header above them.
            setMargins(0, dp(ctx, 4), 0, dp(ctx, 8))
        }
    }
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0)
    }
    suggestions.forEachIndexed { index, suggestion ->
        val chip = TextView(ctx).apply {
            text = suggestion.display
            setTextColor(titleColor)
            typeface = appTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
            background = ContextCompat.getDrawable(
                ctx, R.drawable.app_search_chip_background,
            )
            isClickable = true
            isFocusable = true
            // Use the surface ripple from the parent theme so the chip feels
            // tactile without dragging in extra dependencies.
            val tv = TypedValue()
            ctx.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true,
            )
            foreground = ContextCompat.getDrawable(ctx, tv.resourceId)
            setOnClickListener { onClick(suggestion) }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        if (index > 0) lp.leftMargin = dp(ctx, 8)
        row.addView(chip, lp)
    }
    scroll.addView(row)
    parent.addView(scroll)
}

private fun dp(ctx: Context, value: Int): Int =
    (value * ctx.resources.displayMetrics.density).toInt()

/**
 * Mic tap: try offline Whisper first (private, low-latency), fall back to the
 * cloud SpeechRecognizer if Whisper isn't initialised. Result opens the in-place
 * SearchView with the transcribed text so the user can review and pick a
 * suggestion or submit via IME.
 */
private fun startVoiceCapture(
    ctx: Context,
    searchView: SearchView,
    whisper: WhisperVoiceService,
    cloud: VoiceSearchService,
    onListeningChanged: (Boolean) -> Unit,
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
                    // Mic always opens the in-place SearchView with the transcribed
                    // text so the user sees our suggestion UI (recents + popular
                    // verses filtered by the query) instead of being pushed to the
                    // old feature:search results page. They can pick a suggestion
                    // or hit IME-search to submit.
                    if (!searchView.isShowing) {
                        searchView.show()
                    }
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
