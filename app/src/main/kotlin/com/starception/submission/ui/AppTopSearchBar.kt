package com.starception.submission.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import androidx.core.widget.NestedScrollView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.starception.submission.R
import com.starception.submission.core.data.model.RecentSearchQuery
import com.starception.submission.core.designsystem.icon.topicIconResFor
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.download.MissingContentCard
import com.starception.submission.feature.search.SuggestedVerse
import com.starception.submission.feature.search.SuggestedVerses
import com.starception.submission.feature.search.VoiceSearchService
import com.starception.submission.feature.search.WhisperVoiceService
import com.starception.submission.ui.search.InMemorySearchResult
import com.starception.submission.ui.search.SearchTokenizer
import com.starception.submission.ui.search.PopularSuggestion
import com.starception.submission.ui.search.SearchHintAnimator
import com.starception.submission.ui.search.SearchHints
import com.starception.submission.ui.search.shouldSuppressAmbiguousAllahPrefixVerses
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

@Composable
fun AppTopSearchBar(
    title: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    pillContainerColor: ComposeColor = ComposeColor.Unspecified,
    pillContentColor: ComposeColor = ComposeColor.Unspecified,
    onVerseClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onSearchSubmit: (query: String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    // All non-verse search tap callbacks (topic, news, fortress dua, quranic dua,
    // ayah) flow through this CompositionLocal — set once at NavHost level so
    // direct callers like PrayerTimesScreen also get them without per-screen
    // boilerplate.
    val searchNav = LocalSearchNavCallbacks.current
    val onProfileClick = LocalProfileClick.current
    val profileAvatarUrl = LocalProfileAvatarUrl.current
    val onTopicClick = searchNav.onTopicClick
    val onNewsClick = searchNav.onNewsClick
    val onFortressDuaClick = searchNav.onFortressDuaClick
    val onQuranicDuaClick = searchNav.onQuranicDuaClick
    val onBukhariHadithClick = searchNav.onBukhariHadithClick
    // Use primaryContainer so the SearchBar pill takes on the user's selected
    // brand color (e.g. Royal -> pale Lapis blue) instead of the near-cream
    // surfaceVariant that's visually indistinguishable from the page background.
    val pillBackground = (
        if (pillContainerColor == ComposeColor.Unspecified) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            pillContainerColor
        }
    ).toArgb()
    val pillTextColor = (
        if (pillContentColor == ComposeColor.Unspecified) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            pillContentColor
        }
    ).toArgb()
    // Inflated SearchView ships M3 default lavender — repaint with brand colors.
    val searchViewBg = MaterialTheme.colorScheme.surface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val titleColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // Popular-chip colors must come from the Compose scheme too: the XML shape's
    // ?attr/colorSurfaceContainerHighest resolves against the inflated DayNight
    // View theme, which follows SYSTEM dark mode — not the in-app Dark pref.
    val chipBackground = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val chipStroke = MaterialTheme.colorScheme.outlineVariant.toArgb()
    // Expanded SearchView input pill (the toolbar's shaped background). Its
    // style color resolves from the DayNight View theme once at inflation, so
    // without a repaint it stays black after a dark→light flip until restart.
    val expandedPillColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val topInsetPx = with(LocalDensity.current) { topInset.roundToPx() }
    val currentContent by rememberUpdatedState(content)
    val viewModel = hiltViewModel<TopBarSearchViewModel>()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val inMemoryResults by viewModel.inMemoryResults.collectAsStateWithLifecycle()
    val ayahResults by viewModel.ayahResults.collectAsStateWithLifecycle()
    val fortressDuaResults by viewModel.fortressDuaResults.collectAsStateWithLifecycle()
    val bukhariHadithResults by viewModel.bukhariHadithResults.collectAsStateWithLifecycle()
    val currentOnVerseClick by rememberUpdatedState(onVerseClick)
    val currentOnSearchSubmit by rememberUpdatedState(onSearchSubmit)
    val currentOnTopicClick by rememberUpdatedState(onTopicClick)
    val currentOnNewsClick by rememberUpdatedState(onNewsClick)
    val currentOnFortressDuaClick by rememberUpdatedState(onFortressDuaClick)
    val currentOnQuranicDuaClick by rememberUpdatedState(onQuranicDuaClick)
    val currentOnBukhariHadithClick by rememberUpdatedState(onBukhariHadithClick)
    val currentOnProfileClick by rememberUpdatedState(onProfileClick)

    val context = LocalContext.current
    // Track the live query so renderSuggestions can filter in real time as the
    // user types. mutableStateOf survives recomposition; the TextWatcher writes
    // here and Compose re-invokes update() so the suggestion list re-renders.
    var liveQuery by remember { mutableStateOf("") }
    // Typewriter hint is driven by a process-wide singleton ([SearchHintAnimator])
    // so the cycle keeps running across page switches — every screen wraps
    // itself in its own top-bar scaffold, and without the singleton each new
    // composition would restart the typewriter from the first phrase. The
    // animation visually pauses while the SearchView is open because the pill
    // collapses out of view during the morph; no extra pause logic needed.
    LaunchedEffect(Unit) { SearchHintAnimator.ensureStarted() }
    val animatedHint by SearchHintAnimator.hintText.collectAsStateWithLifecycle()
    var displayedHint by remember { mutableStateOf(animatedHint) }
    val hintTransition = remember { Animatable(0f) }
    LaunchedEffect(animatedHint) {
        if (animatedHint == displayedHint) return@LaunchedEffect
        // Lift and fade the old phrase, swap while invisible, then let the new
        // phrase rise from below. This mirrors the sync and weather text motion.
        hintTransition.animateTo(
            targetValue = -1f,
            animationSpec = tween(150, easing = FastOutSlowInEasing),
        )
        displayedHint = animatedHint
        hintTransition.snapTo(1f)
        delay(55)
        hintTransition.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    val whisperService = remember(context) { WhisperVoiceService(context.applicationContext) }
    // Drive the listening UI (wave + glow + hidden chrome) from the service's
    // actual recording state so it ends the instant recording stops — during
    // transcription the field must not look like it's still capturing.
    val isListening by whisperService.isListening.collectAsStateWithLifecycle()
    val isProcessing by whisperService.isTranscribing.collectAsStateWithLifecycle()
    // Single progress driving the fluid listening transition: chrome (mic +
    // magnifier + hint) fades out and the voice wave slides up as this animates
    // 0 → 1, and the reverse on stop. A light spring gives it an organic settle.
    // Read inside the AndroidView update block, it animates the View chrome
    // frame-by-frame.
    val listenProgress by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "listenProgress",
    )
    // Publish the live capture state + mic level to the shared bus so the bottom
    // voice-assistant button animates in sync with the real listening session
    // (it lives in NiaApp, outside this bar, and has no direct service handle).
    LaunchedEffect(isListening) {
        com.starception.submission.ui.search.SearchPrefillBus.setListening(isListening)
    }
    LaunchedEffect(isProcessing) {
        com.starception.submission.ui.search.SearchPrefillBus.setProcessing(isProcessing)
    }
    LaunchedEffect(whisperService) {
        whisperService.voiceLevel.collect {
            com.starception.submission.ui.search.SearchPrefillBus.setVoiceLevel(it)
        }
    }
    // Safety reset: if this search bar leaves composition while search is open or
    // a capture is live (e.g. tapping a result navigates to a new screen, so the
    // SearchView never fires its HIDDEN transition), clear the shared flags. This
    // stops `isSearchOpen`/`listening` getting stuck true and permanently hiding
    // ambient surfaces such as the floating bottom navigation bar.
    DisposableEffect(Unit) {
        onDispose {
            com.starception.submission.ui.search.SearchPrefillBus.setSearchOpen(false)
            com.starception.submission.ui.search.SearchPrefillBus.setListening(false)
            com.starception.submission.ui.search.SearchPrefillBus.setProcessing(false)
            com.starception.submission.ui.search.SearchPrefillBus.setVoiceLevel(0f)
        }
    }
    // SearchBar's bounds inside the AndroidView, used to position the
    // voice wave overlay precisely around the pill.
    var searchBarBoundsPx by remember { mutableStateOf<Rect?>(null) }
    // Bounds of the expanded SearchView's input toolbar — when the search page
    // is open the glow must wrap that full-width bar, not the collapsed pill.
    var searchViewBarBoundsPx by remember { mutableStateOf<Rect?>(null) }
    var isSearchViewOpen by remember { mutableStateOf(false) }

    DisposableEffect(whisperService) {
        // Warm the model only when its file is actually present (bundled or
        // CDN-downloaded); otherwise the mic tap offers the download instead.
        if (whisperService.isModelAvailable()) whisperService.initialize()
        onDispose { whisperService.release() }
    }

    // Mic tapped with no Whisper model on disk: show the standard missing-content
    // download card (same UI as missing hadith collections) so the user can fetch
    // the offline voice recognition model — never fall back to Google's cloud
    // recognizer.
    var showVoiceModelDownload by remember { mutableStateOf(false) }
    val promptVoiceModelDownload: () -> Unit = remember { { showVoiceModelDownload = true } }

    // Stable holder so the bus collector (Composable-scope) can reach the
    // SearchView that the AndroidView factory creates exactly once.
    val searchViewHolder = remember { mutableStateOf<SearchView?>(null) }
    LaunchedEffect(Unit) {
        com.starception.submission.ui.search.SearchPrefillBus.requests.collect { query ->
            val sv = searchViewHolder.value ?: return@collect
            if (!sv.isAttachedToWindow) return@collect
            sv.show()
            sv.setText(query)
            sv.getEditText().setSelection(query.length)
        }
    }

    // Voice-search requests from ambient surfaces (home bottom Ask bar): run the
    // exact same flow as the search bar's own mic button. The tap handler is
    // created in the AndroidView factory (it needs the SearchView), so it's
    // published through this holder.
    val micTapHolder = remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(Unit) {
        com.starception.submission.ui.search.SearchPrefillBus.voiceRequests.collect {
            micTapHolder.value?.invoke()
        }
    }

    // Mic tap with no RECORD_AUDIO permission triggers this system request; on
    // grant we immediately start capture using the SearchView created by the
    // AndroidView factory (reachable via searchViewHolder from this scope).
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            searchViewHolder.value?.let { sv ->
                startVoiceCapture(
                    ctx = context,
                    searchView = sv,
                    whisper = whisperService,
                    onModelMissing = promptVoiceModelDownload,
                )
            }
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
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
            searchViewHolder.value = searchView
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

            // Track the expanded SearchView's input toolbar the same way so the
            // glow can wrap the whole search input on the search page.
            val searchViewToolbar: View? =
                searchView.findViewById(MaterialR.id.open_search_view_toolbar)
                    ?: (searchView.getEditText().parent as? View)
            searchViewToolbar?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val loc = IntArray(2)
                val rootLoc = IntArray(2)
                v.getLocationInWindow(loc)
                root.getLocationInWindow(rootLoc)
                val l = (loc[0] - rootLoc[0]).toFloat()
                val t = (loc[1] - rootLoc[1]).toFloat()
                searchViewBarBoundsPx = Rect(l, t, l + v.width.toFloat(), t + v.height.toFloat())
            }
            searchView.addTransitionListener { _, _, newState ->
                isSearchViewOpen = newState == SearchView.TransitionState.SHOWN ||
                    newState == SearchView.TransitionState.SHOWING
                // Ambient surfaces (home Ask bar) hide while search is open.
                com.starception.submission.ui.search.SearchPrefillBus.setSearchOpen(isSearchViewOpen)
            }

            // Hint will be overwritten on every `update` pass from the
            // rotating phrase StateFlow; seed it with the current slot's first
            // phrase so the pill isn't blank for the one frame before the
            // singleton emits.
            searchBar.hint = SearchHints.hintFor()
            // Match the rest of the app — Roboto Serif (downloadable Google Font),
            // same family Compose uses via NiaTheme. SearchBar/SearchView are View
            // components so they ignore Compose Typography and default to system sans.
            val appTypeface = appSearchTypeface(ctx)
            searchBar.textView?.setHintTextColor(pillTextColor)
            searchBar.textView?.setTextColor(pillTextColor)
            searchBar.textView?.typeface = appTypeface
            searchView.hint = SearchHints.hintFor()
            searchView.getEditText().typeface = appTypeface
            // Mic tap: capture straight away when permission is held, otherwise
            // fire the system permission request (capture resumes in its callback).
            val onMicTap = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startVoiceCapture(
                        ctx = ctx,
                        searchView = searchView,
                        whisper = whisperService,
                        onModelMissing = promptVoiceModelDownload,
                    )
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            micTapHolder.value = onMicTap
            searchBar.inflateMenu(R.menu.app_top_search_bar_menu)
            searchBar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    onMicTap()
                } else {
                    searchView.show()
                }
                true
            }
            searchView.inflateMenu(R.menu.app_top_search_bar_menu)
            searchView.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_mic) {
                    onMicTap()
                }
                true
            }

            leading.setOnClickListener { currentOnProfileClick() }
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
            val leading = root.findViewById<MaterialButton>(R.id.leading_button)
            val settings = root.findViewById<MaterialButton>(R.id.settings_button)

            val searchView = root.findViewById<SearchView>(R.id.app_search_view)
            // Drive the SearchBar's hint from the typewriter state; the
            // SearchView shows its own static placeholder when expanded.
            // While voice capture is live the hints are cleared so the centered
            // voice wave owns the field without text bleeding through it.
            // Hints stay set through the transition and fade via their text-color
            // alpha (chromeVisible) so the field hands off to the wave smoothly
            // instead of blanking instantly.
            val chromeVisible = (1f - listenProgress).coerceIn(0f, 1f)
            searchBar.hint = displayedHint
            searchView.hint = SearchHints.hintFor()
            val fadedHintColor = (pillTextColor and 0x00FFFFFF) or ((chromeVisible * 255f).toInt() shl 24)
            searchBar.textView?.setHintTextColor(fadedHintColor)
            searchBar.textView?.setTextColor(pillTextColor)
            // Native View icons read tints from the Activity's Configuration
            // (system dark mode), not from the user's app-level Dark pref — so
            // we tint them manually to match the active Compose colorScheme.
            // Buttons outside the pill sit on the AppBar/window surface → onSurface.
            // Icons inside the SearchBar pill (magnifier + mic) → onPrimaryContainer.
            // Leading button shows the signed-in user's circular avatar when logged in,
            // otherwise the default profile glyph (tinted to match the theme). The URL is
            // stored as a tag so Coil only re-enqueues when the avatar actually changes.
            val density = root.resources.displayMetrics.density
            // Leading icon stays a constant 34dp in every auth state (avatar photo
            // when signed in, profile glyph otherwise) so the pill never shifts on
            // login/logout. The search bar's asymmetric start/end margins (12dp/8dp
            // in XML) offset this larger icon against the 26dp settings glyph so the
            // gaps around the pill stay symmetric.
            if (profileAvatarUrl != null) {
                leading.iconTint = null
                // Larger icon so the gradient ring has room to read clearly.
                leading.iconSize = (34f * density).toInt()
                if (leading.getTag(R.id.leading_button) != profileAvatarUrl) {
                    leading.setTag(R.id.leading_button, profileAvatarUrl)
                    val iconPx = (34f * density).toInt()
                    val request = ImageRequest.Builder(root.context)
                        .data(profileAvatarUrl)
                        // Decode the source larger than the icon for a crisp photo, but
                        // let the transform deliver the bitmap at the icon size so the
                        // MaterialButton draws it 1:1 (no soft/pixelated rescale).
                        .size(CoilSize(iconPx * 3, iconPx * 3))
                        // Slightly thicker ring than the default for this small icon.
                        .transformations(RingAvatarTransformation(ringFraction = 0.06f, outputPx = iconPx))
                        .target(
                            onSuccess = { drawable ->
                                leading.iconTint = null
                                leading.icon = drawable
                            },
                            onError = {
                                leading.iconTint = ColorStateList.valueOf(titleColor)
                                leading.iconSize = (34f * density).toInt()
                                leading.icon = ContextCompat.getDrawable(
                                    root.context,
                                    R.drawable.ic_app_top_bar_profile_24,
                                )
                            },
                        )
                        .build()
                    root.context.imageLoader.enqueue(request)
                }
            } else {
                if (leading.getTag(R.id.leading_button) != null) {
                    leading.setTag(R.id.leading_button, null)
                    leading.icon = ContextCompat.getDrawable(
                        root.context,
                        R.drawable.ic_app_top_bar_profile_24,
                    )
                }
                leading.iconSize = (34f * density).toInt()
                leading.iconTint = ColorStateList.valueOf(titleColor)
            }
            settings.iconTint = ColorStateList.valueOf(titleColor)
            searchBar.navigationIcon?.mutate()?.setTint(pillTextColor)
            for (i in 0 until searchBar.menu.size()) {
                searchBar.menu.getItem(i).icon?.mutate()?.setTint(pillTextColor)
            }
            for (i in 0 until searchView.toolbar.menu.size()) {
                searchView.toolbar.menu.getItem(i).icon?.mutate()?.setTint(titleColor)
            }
            searchView.toolbar.navigationIcon?.mutate()?.setTint(titleColor)

            // Fade the pill's chrome (magnifier nav icon, back arrow, and mic menu
            // icon) in lockstep with the listening transition so it hands off to /
            // from the wave fluidly. Menu items stay present (just transparent)
            // until fully listening, so their icons fade rather than pop out.
            val chromeAlpha = (chromeVisible * 255f).toInt()
            searchView.toolbar.navigationIcon?.mutate()?.alpha = chromeAlpha
            searchBar.navigationIcon?.mutate()?.alpha = chromeAlpha
            val menuHidden = listenProgress > 0.995f
            for (i in 0 until searchView.toolbar.menu.size()) {
                val item = searchView.toolbar.menu.getItem(i)
                item.isVisible = !menuHidden
                item.icon?.mutate()?.alpha = chromeAlpha
            }
            for (i in 0 until searchBar.menu.size()) {
                val item = searchBar.menu.getItem(i)
                item.isVisible = !menuHidden
                item.icon?.mutate()?.alpha = chromeAlpha
            }
            // "Dive into the wave": as listening begins, translate the mic,
            // magnifier / back arrow and hint downward so the chrome appears to
            // sink into the rising wave — and surface back up on stop. The fade is
            // the alpha above; this adds the vertical motion where the host views
            // are reachable (a no-op otherwise, so the fade still applies). The
            // Toolbar clips its children, so the icons are cut off as they pass the
            // pill's bottom edge, completing the "vanish" illusion.
            val sinkPx = searchBar.height * 0.7f * listenProgress
            searchBar.findViewById<View>(R.id.action_mic)?.translationY = sinkPx
            searchBar.firstImageButtonChild()?.translationY = sinkPx
            val hintShiftPx = 7f * density * hintTransition.value
            searchBar.textView?.translationY = sinkPx + hintShiftPx
            searchBar.textView?.alpha =
                (1f - hintTransition.value.absoluteValue).coerceIn(0f, 1f)
            searchView.findViewById<View>(R.id.action_mic)?.translationY = sinkPx
            searchView.toolbar.firstImageButtonChild()?.translationY = sinkPx
            searchView.getEditText().isCursorVisible = !isListening

            appBar.setPadding(0, topInsetPx, 0, 0)

            // Re-apply the pill tint on every update pass, not just at factory
            // time: the in-app Dark pref recomposes Compose without re-inflating
            // this View, so a factory-only tint goes stale on theme flips (and on
            // cold start, where the pref loads after first inflation).
            searchBar.backgroundTintList = ColorStateList.valueOf(pillBackground)

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
            // The expanded input pill is the toolbar's OWN MaterialShapeDrawable
            // (expressive AppBarWithSearch style) — tint it so the stadium shape
            // survives while the fill follows the Compose scheme.
            searchView.toolbar.backgroundTintList = ColorStateList.valueOf(expandedPillColor)
            root.findViewById<View?>(
                MaterialR.id.open_search_view_divider
            )?.setBackgroundColor(chipStroke)
            searchView.getEditText().setTextColor(titleColor)
            // Hint + clear (✕) button otherwise keep their DayNight (system)
            // colors — unreadable when the app-level Dark pref disagrees.
            searchView.getEditText().setHintTextColor(subtitleColor)
            searchView.findViewById<ImageButton?>(
                MaterialR.id.open_search_view_clear_button
            )?.setColorFilter(titleColor)

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
                bukhariHadithResults = bukhariHadithResults,
                accentColor = accentColor,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                chipBackground = chipBackground,
                chipStroke = chipStroke,
                onVerseClick = { surah, ayah ->
                    searchView.hide()
                    currentOnVerseClick(surah, ayah)
                },
                onRecentClick = { query ->
                    // Refill the SearchView so inline FTS re-runs against the
                    // chosen recent query — don't dump the user back on the old
                    // feature:search page.
                    searchView.setText(query)
                    // setText leaves the cursor at position 0 — move it to the
                    // end so the user can immediately edit/extend the query.
                    searchView.getEditText().setSelection(query.length)
                },
                onPopularClick = { suggestion ->
                    // Tapping a popular-search chip refills the SearchView with the
                    // chip's visible text (WYSIWYG) so the full ranked-search pipeline
                    // runs against the same string the user just tapped.
                    searchView.setText(suggestion.display)
                    searchView.getEditText().setSelection(suggestion.display.length)
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
                onBukhariHadithClick = { hadithNumber ->
                    viewModel.saveSearchQuery(liveQuery)
                    searchView.hide()
                    currentOnBukhariHadithClick(hadithNumber)
                },
            )

            composeView?.setContent {
                // Read from ThemeColorBridge so the inner island honours the user's
                // app-level Dark/Brand pref instead of NiaTheme's COASTAL/system-dark
                // defaults — otherwise the body stays light while the outer toolbar
                // and bottom nav (which sit outside this ComposeView) follow Dark.
                // A previewOverride (set by the theme-picker preview) wins over the
                // active theme so the candidate palette reaches this island too.
                val bridge = com.starception.submission.util.ThemeColorBridge
                val override = bridge.previewOverride
                val seedArgb = override?.customSeedArgb ?: bridge.customSeedColor
                val secondaryArgb = override?.customSecondaryArgb ?: bridge.customSecondaryColor
                val tertiaryArgb = override?.customTertiaryArgb ?: bridge.customTertiaryColor
                NiaTheme(
                    darkTheme = bridge.darkTheme,
                    themeBrand = override?.brand ?: bridge.themeBrand,
                    customSeedColor = if (seedArgb != 0) ComposeColor(seedArgb) else ComposeColor.Unspecified,
                    customSecondaryColor = if (secondaryArgb != 0) ComposeColor(secondaryArgb) else ComposeColor.Unspecified,
                    customTertiaryColor = if (tertiaryArgb != 0) ComposeColor(tertiaryArgb) else ComposeColor.Unspecified,
                    disableDynamicTheming = bridge.disableDynamicTheming,
                ) { currentContent() }
            }
        },
    )
    // Voice wave overlay: the animated visualization that fills the SearchBar
    // pill while the mic is capturing. It sits above the AndroidView so the
    // SearchBar stays interactive when not listening. On the search page it
    // spans the SearchView's full input bar; on the home page the collapsed pill.
    val bounds = if (isSearchViewOpen) {
        searchViewBarBoundsPx ?: searchBarBoundsPx
    } else {
        searchBarBoundsPx
    }
    // Keep the wave composed through the whole transition (progress > 0), not
    // just while actively listening, so it can slide and fade out gracefully on
    // stop instead of vanishing the instant recording ends.
    if (bounds != null && listenProgress > 0.001f) {
        // Live microphone level from the Whisper recorder, smoothed so the wave
        // moves organically rather than jittering. The raw VOICE_RECOGNITION
        // source registers speech around 0.01-0.03 RMS, so scale up for a
        // visible wave.
        val whisperLevel by whisperService.voiceLevel.collectAsStateWithLifecycle()
        val smoothedLevel by animateFloatAsState(
            targetValue = (whisperLevel * 15f).coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 400f,
            ),
            label = "voiceLevel",
        )
        VoiceWaveOverlay(
            bounds = bounds,
            level = smoothedLevel,
            appearProgress = listenProgress,
            modifier = Modifier.matchParentSize(),
        )
    }
    if (showVoiceModelDownload) {
        // Full-screen missing-content page — same layout as the hadith detail
        // screen's download prompt: solid surface, centered card, circular back.
        BackHandler { showVoiceModelDownload = false }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MissingContentCard(
                    resourceName = "Voice Recognition",
                    category = "model_whisper",
                    description = "The offline voice recognition model needs to be downloaded. " +
                        "Voice search then runs fully on-device.",
                    downloadManager = viewModel.getDownloadManager(),
                    onDownloadComplete = {
                        whisperService.initialize()
                        showVoiceModelDownload = false
                        Toast.makeText(
                            context,
                            "Voice recognition ready — tap the mic to try it",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
            // Back button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                IconButton(onClick = { showVoiceModelDownload = false }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    }
}

/**
 * Voice wave: an organic range of rolling hills sitting flush on the bottom edge
 * of the search pill while the mic is capturing. Three parallax layers (two
 * receding grey ranges behind a [primary] hero range with a gradient sheen and a
 * crisp ridgeline) drift horizontally for depth, and their height rides the live
 * microphone [level] through a concave response so even a little speech makes the
 * range swell dramatically. Colors come from the active theme (nothing
 * hardcoded); drawing is clipped to the pill so the fill follows its rounded
 * corners.
 */
@Composable
private fun VoiceWaveOverlay(
    bounds: Rect,
    level: Float,
    appearProgress: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    // Mid range blends the grey toward the hero so the three layers read as one
    // receding range rather than two unrelated colors.
    val midColor = lerp(tertiary, primary, 0.45f)

    Canvas(modifier = modifier) {
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

        // Chrome (back arrow, mic, cursor) is hidden while listening, so the
        // hills span the full width of the pill.
        val startX = bounds.left
        val endX = bounds.right
        val span = endX - startX
        if (span <= 0f) return@Canvas

        val twoPi = 2f * Math.PI.toFloat()
        val baseline = bounds.bottom
        val ceiling = (baseline - bounds.top) * 0.96f

        // Concave amplitude response: a small amount of voice already lifts the
        // hills high, so quiet speech still reads as a big, fancy swell while
        // silence keeps a calm low range.
        val voice = kotlin.math.sqrt((level * 2.2f).coerceIn(0f, 1f))
        val amp = 0.28f + 0.72f * voice
        val appear = appearProgress.coerceIn(0f, 1f)

        // Ramps height from 0 at the very edges up across the outer ~14% so the
        // range meets the pill ends without a vertical wall.
        fun edgeFade(u: Float): Float =
            (kotlin.math.min(u, 1f - u) / 0.14f).coerceIn(0f, 1f)

        // Organic silhouette: three sine octaves that drift horizontally over
        // time, squared to deepen the valleys into distinct rounded hills of
        // varying height (rather than identical bumps). Integer time multipliers
        // keep the drift seamless across the animation loop.
        fun profile(u: Float, phase: Float, speed: Float): Float {
            val a = kotlin.math.sin((u * 2.6f + t * speed + phase) * twoPi)
            val b = 0.45f * kotlin.math.sin((u * 5.1f - t * (speed + 1f) + phase * 1.7f) * twoPi)
            val c = 0.22f * kotlin.math.sin((u * 9.7f + t * (speed + 2f) + phase * 2.3f) * twoPi)
            val ridge = ((a + b + c) / 1.67f) * 0.5f + 0.5f
            return ridge * ridge
        }

        val samples = 140
        // Closed fill path plus the open top polyline (for the ridgeline stroke).
        fun ridge(phase: Float, speed: Float, scale: Float): Pair<Path, Path> {
            val fill = Path()
            val top = Path()
            fill.moveTo(startX, baseline)
            for (s in 0..samples) {
                val u = s / samples.toFloat()
                val x = startX + u * span
                val y = baseline - profile(u, phase, speed) * edgeFade(u) * ceiling * amp * scale
                fill.lineTo(x, y)
                if (s == 0) top.moveTo(x, y) else top.lineTo(x, y)
            }
            fill.lineTo(endX, baseline)
            fill.close()
            return fill to top
        }

        // Clip to the pill (a stadium: corner radius = half its height) so the
        // fill follows the rounded corners and nothing spills past them.
        val pill = Path().apply {
            addRoundRect(
                RoundRect(
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    cornerRadius = CornerRadius(bounds.height / 2f),
                ),
            )
        }
        // Slide the whole range up from below the pill as it appears (and back
        // down on stop); the pill clip hides the offset part so it reads as the
        // wave rising out of the bar. `appear` also fades every layer in/out.
        clipPath(pill) {
            translate(top = (1f - appear) * bounds.height) {
                // Two receding grey ranges give parallax depth behind the hero.
                drawPath(
                    path = ridge(phase = 0.55f, speed = 1f, scale = 1f).first,
                    color = tertiary,
                    alpha = 0.26f * appear,
                )
                drawPath(
                    path = ridge(phase = 0.28f, speed = 2f, scale = 0.9f).first,
                    color = midColor,
                    alpha = 0.34f * appear,
                )
                // Hero range: a soft vertical gradient sheen and a crisp ridgeline
                // that brightens and thickens as the voice rises.
                val (frontFill, frontTop) = ridge(phase = 0f, speed = 3f, scale = 0.94f)
                drawPath(
                    path = frontFill,
                    brush = Brush.verticalGradient(
                        colors = listOf(primary.copy(alpha = 0.96f), primary.copy(alpha = 0.72f)),
                        startY = bounds.top,
                        endY = baseline,
                    ),
                    alpha = appear,
                )
                drawPath(
                    path = frontTop,
                    color = primary,
                    alpha = (0.85f + 0.15f * voice) * appear,
                    style = Stroke(width = 1.5.dp.toPx() + 0.6.dp.toPx() * voice, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * The Toolbar's navigation button (the magnifier on the SearchBar, the back
 * arrow on the SearchView) is a direct [ImageButton] child, unlike menu items.
 * Returns it so the listening transition can translate it down into the wave, or
 * null if the navigation icon isn't present yet.
 */
private fun ViewGroup.firstImageButtonChild(): View? {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child is ImageButton) return child
    }
    return null
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
 * service (surahs, Quranic duas, popular verses, Fortress invocations) plus SQL
 * hits (ayahs, Fortress invocations, FTS topics + news). Sections are ordered by the highest-scoring
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
    bukhariHadithResults: List<com.starception.submission.core.hadithdatabase.HadithEntity>,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    chipBackground: Int,
    chipStroke: Int,
    onVerseClick: (Int, Int) -> Unit,
    onRecentClick: (String) -> Unit,
    onPopularClick: (PopularSuggestion) -> Unit,
    onTopicClick: (String) -> Unit,
    onNewsClick: (com.starception.submission.core.model.data.UserNewsResource) -> Unit,
    onFortressDuaClick: (com.starception.submission.core.duadatabase.Dua) -> Unit,
    onQuranicDuaClick: (com.starception.submission.core.quranicduas.QuranicDuaEntity) -> Unit,
    onBukhariHadithClick: (Int) -> Unit,
) {
    val trimmedQuery = query.trim()
    val isFiltering = trimmedQuery.isNotEmpty()

    // One flat "RECENT" list (newest first) — bucketing by YESTERDAY / THIS WEEK
    // felt fussy for a search shortcut. Cap so a chatty history doesn't push
    // the curated verses below the fold.
    val filteredRecent = (
        if (isFiltering) recentSearches.filter { it.query.contains(trimmedQuery, ignoreCase = true) }
        else recentSearches
        ).take(MAX_RECENTS_TOTAL)
    val popularSuggestions = if (isFiltering) emptyList() else SearchHints.popularSuggestions()
    // Curated highlight verses (Ayatul Kursi, Al-Fatiha, Ar-Rahman, etc.) shown
    // on the empty state so users can jump straight to the staples without
    // typing. Hidden once they start searching — the ranked verseIndex inside
    // inMemoryResults takes over for filtered matches.
    // Rendered in batches: the first batch up front, more appended as the user
    // scrolls toward the bottom of the NestedScrollView.
    val emptyStateVerses = if (isFiltering) emptyList() else SuggestedVerses.verses

    // In-memory ranked hits (already sorted by score). Cap per-section so the
    // suggestion list stays scannable.
    val rankedSurahs = inMemoryResults.surahs.take(5)
    val rankedQuranicDuas = inMemoryResults.quranicDuas.take(5)
    val rankedVerses = inMemoryResults.verses.take(5)
    val rankedFortressDuas = inMemoryResults.fortressDuas.take(8)
    // SQL hits (already capped and sorted at the DAO level)
    val cappedAyahs = ayahResults.take(8)
    // Ranked fuzzy hits lead, then exact SQL content hits fill the section.
    // A dua can match both paths, so deduplicate by its stable database ID.
    val cappedFortressDuas = (
        rankedFortressDuas.map { it.item } + fortressDuaResults
        ).distinctBy { it.id }.take(8)
    val cappedBukhariHadiths = bukhariHadithResults.take(8)
    val ftsTopics = searchResults.topics.take(5)
    val ftsNews = searchResults.newsResources
        .filterNot { news ->
            cappedBukhariHadiths.isNotEmpty() &&
                news.url.startsWith("hadith://sahih_bukhari/")
        }
        .take(8)

    val stateKey = buildString {
        append(trimmedQuery)
        append("##")
        append(filteredRecent.joinToString("|") { it.query })
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
        append(cappedBukhariHadiths.joinToString("|") { "b${it.id}" })
        append("##")
        append(ftsTopics.joinToString("|") { it.topic.id })
        append("##")
        append(ftsNews.joinToString("|") { it.id })
        append("##")
        // Palette in the key so an in-app theme flip (same data, new colors)
        // still repaints rows that would otherwise be skipped as unchanged.
        append(accentColor)
        append(':')
        append(titleColor)
        append(':')
        append(subtitleColor)
        append(':')
        append(chipBackground)
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
    val rawWords = SearchTokenizer.splitWords(SearchTokenizer.normalize(trimmedQuery)).toSet()
    val surahIntent = SearchTokenizer.hasSurahIntent(trimmedQuery)
    val duaIntent = "dua" in rawWords || "duas" in rawWords
    val verseIntent = "verse" in rawWords || "ayah" in rawWords || "ayat" in rawWords ||
        "verses" in rawWords
    val intentBoost: (Boolean) -> Double = { if (it) INTENT_BOOST else 0.0 }
    // Once an explicit Surah command has a ranked match, keep the surface
    // focused. Otherwise a fuzzy fragment such as "Asd" can match ordinary
    // invocation prose and place unrelated duas above the requested chapter.
    val restrictToSurahs = surahIntent && rankedSurahs.isNotEmpty()
    val suppressAmbiguousAllahPrefixVerses = shouldSuppressAmbiguousAllahPrefixVerses(
        query = trimmedQuery,
        hasSurahMatches = rankedSurahs.isNotEmpty(),
    )
    val surahCorrection = if (restrictToSurahs) {
        inferredSurahCorrection(trimmedQuery, rankedSurahs.first().item)
    } else {
        null
    }
    val sections = mutableListOf<RenderableSection>()
    if (!restrictToSurahs &&
        !suppressAmbiguousAllahPrefixVerses &&
        rankedVerses.isNotEmpty()
    ) {
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
    if (!restrictToSurahs && rankedQuranicDuas.isNotEmpty()) {
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
    if (!restrictToSurahs && cappedAyahs.isNotEmpty()) {
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
    if (!restrictToSurahs && cappedFortressDuas.isNotEmpty()) {
        sections.add(
            RenderableSection(
                title = "Fortress of the Muslim",
                score = maxOf(
                    SQL_FORTRESS_PRIOR,
                    rankedFortressDuas.firstOrNull()?.score ?: 0.0,
                ) + intentBoost(duaIntent),
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
    if (!restrictToSurahs && cappedBukhariHadiths.isNotEmpty()) {
        sections.add(
            RenderableSection(title = "Sahih Bukhari", score = SQL_BUKHARI_PRIOR) {
                cappedBukhariHadiths.forEach { hadith ->
                    addBukhariHadithItem(
                        parent = container,
                        inflater = inflater,
                        hadith = hadith,
                        accentColor = accentColor,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                    ) { onBukhariHadithClick(hadith.id) }
                }
            },
        )
    }
    if (!restrictToSurahs && ftsTopics.isNotEmpty()) {
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
    if (!restrictToSurahs && ftsNews.isNotEmpty()) {
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
    surahCorrection?.let { correction ->
        addSearchCorrection(container, correction, accentColor)
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
            titleColor, chipBackground, chipStroke, onPopularClick,
        )
    }

    // Recents only help when the user has nothing to act on yet — once any
    // real content section has matches, hide them so the relevant hits aren't
    // pushed below the fold.
    val hasContentHits = sections.isNotEmpty()
    if (!hasContentHits && filteredRecent.isNotEmpty()) {
        addSectionTitle(
            container, inflater,
            ctx.getString(R.string.app_search_section_recent), subtitleColor,
        )
        filteredRecent.forEach { recent ->
            addRecentSearchItem(container, inflater, recent.query, titleColor, subtitleColor, onRecentClick)
        }
    }
    // Empty-state highlight verses (Ayatul Kursi, Al-Fatiha, Ar-Rahman, …) so
    // the staples are always one tap away even with an empty recents list.
    val scrollParent = container.parent as? NestedScrollView
    if (emptyStateVerses.isNotEmpty()) {
        addSectionTitle(
            container, inflater,
            ctx.getString(R.string.app_search_section_popular_verses), subtitleColor,
        )
        var rendered = 0
        fun appendNextBatch() {
            val end = minOf(rendered + EMPTY_STATE_VERSES_BATCH, emptyStateVerses.size)
            emptyStateVerses.subList(rendered, end).forEach { verse ->
                addVerseItem(
                    container, inflater, verse,
                    accentColor, titleColor, subtitleColor, onVerseClick,
                )
            }
            rendered = end
        }
        // Stale-render guard for async callbacks: skip if the container has
        // been re-rendered for a different query/state since we were attached.
        fun isCurrentRender() = container.getTag(SUGGESTION_STATE_TAG.hashCode()) == stateKey

        appendNextBatch()
        // If the first batch (plus recents/chips above it) doesn't overflow the
        // viewport, the scroll listener can never fire — keep appending until
        // the content is actually scrollable or the list is exhausted.
        fun fillUntilScrollable() {
            val parent = scrollParent ?: return
            if (!isCurrentRender() || rendered >= emptyStateVerses.size) return
            if (container.height <= parent.height) {
                appendNextBatch()
                parent.post { fillUntilScrollable() }
            }
        }
        scrollParent?.post { fillUntilScrollable() }
        scrollParent?.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
                if (isCurrentRender() &&
                    rendered < emptyStateVerses.size &&
                    scrollY + v.height * 2 >= container.height
                ) {
                    appendNextBatch()
                }
            },
        )
    } else {
        scrollParent?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
    }
}

private const val MAX_RECENTS_TOTAL = 6
private const val EMPTY_STATE_VERSES_BATCH = 8

private var cachedAppSearchTypeface: Typeface? = null

/**
 * Ubuntu Sans is a downloadable Google Play Services font. Emulator images
 * without the font provider can throw Resources.NotFoundException instead of
 * returning null, so every View-based search surface uses this safe fallback.
 */
private fun appSearchTypeface(context: Context): Typeface {
    cachedAppSearchTypeface?.let { return it }
    return (
        runCatching { ResourcesCompat.getFont(context, R.font.ubuntu_sans) }.getOrNull()
            ?: Typeface.create("sans-serif", Typeface.NORMAL)
        ).also { cachedAppSearchTypeface = it }
}

/** Holds a ranked section's title, lead score, and render closure. */
private data class RenderableSection(
    val title: String,
    val score: Double,
    val render: () -> Unit,
)

// Fixed priors for SQL sources so they sort sensibly when in-memory hits don't
// dominate. These share the in-memory indices' 0..1 confidence scale: a hit
// above ~0.65 is a strong, specific in-memory match and outranks them, while
// weaker ones fall below and the natural order holds: ayahs > fortress >
// topics > news. (They were 18/16/14/12 when in-memory scores were unbounded
// sums; left there, every SQL section would now outrank every in-memory one.)
private const val SQL_AYAH_PRIOR = 0.60
private const val SQL_FORTRESS_PRIOR = 0.54
private const val SQL_BUKHARI_PRIOR = 0.52
private const val FTS_TOPICS_PRIOR = 0.46
private const val FTS_NEWS_PRIOR = 0.40

// Boost applied when the raw query contains a kind word ("surah", "dua",
// "verse") matching the section, so user intent ("Surah imran" → Surahs) pins
// reliably without over-promoting incidental matches.
//
// Half the scale, because the in-memory indices now score 0..1 rather than in
// arbitrary magnitudes: it lifts a fuzzy-only match (≈0.4-0.6) above ordinary
// competition, while a near-perfect match in another section can still hold its
// place. The old value of 25 was calibrated against raw sums and would now make
// the boost absolute rather than strong.
private const val INTENT_BOOST = 0.5

private fun inferredSurahCorrection(
    query: String,
    surah: com.starception.submission.core.qurandatabase.SurahEntity,
): String? {
    val parsed = SearchTokenizer.parse(query)
    val queryToken = parsed.tokens.singleOrNull() ?: return null
    if (queryToken == surah.number.toString()) return null
    val displayName = surah.nameEnglish?.takeIf(String::isNotBlank)
        ?: surah.nameTranslation?.takeIf(String::isNotBlank)
        ?: return null
    val queryKey = SearchTokenizer.transliterationKey(queryToken)
    val nameKeys = SearchTokenizer.indexWords(SearchTokenizer.normalize(displayName))
        .map(SearchTokenizer::transliterationKey)
    if (nameKeys.any { key -> key == queryKey || key.startsWith(queryKey) }) return null
    return "Showing closest match: Surah ${surah.number} — $displayName"
}

private fun addSearchCorrection(parent: ViewGroup, text: String, accentColor: Int) {
    val density = parent.resources.displayMetrics.density
    parent.addView(
        TextView(parent.context).apply {
            this.text = text
            setTextColor(accentColor)
            typeface = appSearchTypeface(parent.context)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(
                (24f * density).toInt(),
                (14f * density).toInt(),
                (24f * density).toInt(),
                (2f * density).toInt(),
            )
        },
    )
}


private fun addSectionTitle(parent: ViewGroup, inflater: LayoutInflater, text: String, subtitleColor: Int) {
    val view = inflater.inflate(R.layout.app_search_suggestion_title, parent, false) as TextView
    view.text = text
    view.setTextColor(subtitleColor)
    view.typeface = appSearchTypeface(parent.context)
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
    val appTypeface = appSearchTypeface(parent.context)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        // Same Twemoji icon as the Holy Quran topic; colorful, so no tint.
        setImageResource(topicIconResFor("quran") ?: R.drawable.ic_app_search_home_24)
        imageTintList = null
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
    val appTypeface = appSearchTypeface(parent.context)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        // Same Twemoji icon as the Holy Quran topic; colorful, so no tint.
        setImageResource(topicIconResFor("quran") ?: R.drawable.ic_app_search_home_24)
        imageTintList = null
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
    val appTypeface = appSearchTypeface(parent.context)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        // Same Twemoji icon as the Quranic Duas topic; colorful, so no tint.
        setImageResource(topicIconResFor("dua") ?: R.drawable.ic_app_search_home_24)
        imageTintList = null
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
    val appTypeface = appSearchTypeface(parent.context)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        // Same Twemoji icon as the Quranic Duas topic; colorful, so no tint.
        setImageResource(topicIconResFor("dua") ?: R.drawable.ic_app_search_home_24)
        imageTintList = null
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

private fun addBukhariHadithItem(
    parent: ViewGroup,
    inflater: LayoutInflater,
    hadith: com.starception.submission.core.hadithdatabase.HadithEntity,
    accentColor: Int,
    titleColor: Int,
    subtitleColor: Int,
    onClick: () -> Unit,
) {
    val view = inflater.inflate(R.layout.app_search_suggestion_item, parent, false)
    val appTypeface = appSearchTypeface(parent.context)
    val book = com.starception.submission.core.model.data.BukhariBooks.findByHadithId(hadith.id)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        setImageResource(topicIconResFor("Sahih Bukhari") ?: R.drawable.ic_app_search_home_24)
        imageTintList = null
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_title).apply {
        text = buildString {
            append("Hadith #${hadith.id}")
            book?.let { append(" · ${it.nameEnglish}") }
        }
        setTextColor(titleColor)
        typeface = appTypeface
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    view.findViewById<TextView>(R.id.app_search_suggestion_subtitle).apply {
        val snippet = hadith.textPlain.orEmpty()
            .replace(Regex("[\\n\\r]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        text = snippet
        setTextColor(subtitleColor)
        typeface = appTypeface
        maxLines = 2
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
    val appTypeface = appSearchTypeface(parent.context)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        val topicIconRes = topicIconResFor(name)
        if (topicIconRes != null) {
            // Same Twemoji icon as the Interests screen; colorful, so no tint.
            setImageResource(topicIconRes)
            imageTintList = null
        } else {
            setImageResource(R.drawable.ic_app_search_home_24)
            imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
        }
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
    val appTypeface = appSearchTypeface(parent.context)
    view.findViewById<ImageView>(R.id.app_search_suggestion_icon).apply {
        // Articles are topical ("Quranic Dua 4: …"), so reuse the topic keyword →
        // Twemoji mapping; colorful icons take no tint. Unmatched titles keep the
        // home glyph.
        val topicIconRes = topicIconResFor(title)
        if (topicIconRes != null) {
            setImageResource(topicIconRes)
            imageTintList = null
        } else {
            setImageResource(R.drawable.ic_app_search_home_24)
            imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
        }
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
    val appTypeface = appSearchTypeface(parent.context)
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
    val appTypeface = appSearchTypeface(parent.context)
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
    titleColor: Int,
    chipBackground: Int,
    chipStroke: Int,
    onClick: (PopularSuggestion) -> Unit,
) {
    val ctx = parent.context
    val appTypeface = appSearchTypeface(ctx)
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
            // Built in code from the Compose palette — the XML shape's ?attr
            // colors resolve against the DayNight View theme (system dark),
            // which disagrees with the in-app Dark pref.
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 20).toFloat()
                setColor(chipBackground)
                setStroke(dp(ctx, 1), chipStroke)
            }
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
 * Mic tap: offline Whisper only (private, on-device — never Google's cloud
 * recognizer). When the model file isn't on disk yet, [onModelMissing] fires so
 * the caller can offer the one-time download via the asset download manager.
 * Result opens the in-place SearchView with the transcribed text so the user
 * can review and pick a suggestion or submit via IME.
 */
private fun startVoiceCapture(
    ctx: Context,
    searchView: SearchView,
    whisper: WhisperVoiceService,
    onModelMissing: () -> Unit,
) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Toast.makeText(ctx, "Microphone permission required", Toast.LENGTH_SHORT).show()
        return
    }

    // The same floating button that starts capture becomes the stop control
    // while its five-bar processing ripple is visible.
    if (whisper.isTranscribing.value) {
        if (whisper.cancelTranscription()) {
            Toast.makeText(ctx, "Voice processing cancelled", Toast.LENGTH_SHORT).show()
        }
        return
    }

    // Second tap while listening = stop now and transcribe what was said
    // (recording also auto-stops on its own after a pause in speech).
    if (whisper.isListening.value) {
        whisper.stopListening()
        return
    }

    if (!whisper.isInitialized.value && !whisper.isModelAvailable()) {
        onModelMissing()
        return
    }

    com.starception.submission.ui.search.SearchPrefillBus.clearVoiceFeedback()

    val handleResult: (VoiceSearchService.VoiceSearchResult) -> Unit = { result ->
        when (result) {
            is VoiceSearchService.VoiceSearchResult.Success -> {
                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    com.starception.submission.ui.search.SearchPrefillBus.clearVoiceFeedback()
                    // Mic always opens the in-place SearchView with the transcribed
                    // text so the user sees our suggestion UI (recents + popular
                    // verses filtered by the query) instead of being pushed to the
                    // old feature:search results page. They can pick a suggestion
                    // or hit IME-search to submit.
                    if (!searchView.isShowing) {
                        searchView.show()
                    }
                    searchView.setText(text)
                    searchView.getEditText().setSelection(text.length)
                }
            }
            is VoiceSearchService.VoiceSearchResult.Error -> {
                Toast.makeText(ctx, result.message, Toast.LENGTH_SHORT).show()
            }
            VoiceSearchService.VoiceSearchResult.Cancelled -> {
                com.starception.submission.ui.search.SearchPrefillBus.showVoiceRetryPrompt()
            }
        }
    }

    // startListening self-initializes when the model file is present but the
    // context hasn't been loaded yet (e.g. right after the download finished).
    // The listening UI follows whisper.isListening, so no manual state here.
    whisper.startListening(handleResult)
}
