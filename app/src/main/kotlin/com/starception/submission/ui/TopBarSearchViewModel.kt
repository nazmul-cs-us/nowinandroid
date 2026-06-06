package com.starception.submission.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.data.model.RecentSearchQuery
import com.starception.submission.core.data.repository.RecentSearchRepository
import com.starception.submission.core.domain.GetRecentSearchQueriesUseCase
import com.starception.submission.core.domain.GetSearchContentsUseCase
import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.core.duadatabase.DuaRepository
import com.starception.submission.core.model.data.UserSearchResult
import com.starception.submission.core.qurandatabase.AyahEntity
import com.starception.submission.core.qurandatabase.QuranDao
import com.starception.submission.ui.search.InMemorySearchResult
import com.starception.submission.ui.search.InMemorySearchService
import com.starception.submission.ui.search.SearchTokenizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TopBarSearchViewModel @Inject constructor(
    getRecentSearchQueriesUseCase: GetRecentSearchQueriesUseCase,
    private val recentSearchRepository: RecentSearchRepository,
    getSearchContentsUseCase: GetSearchContentsUseCase,
    private val duaRepository: DuaRepository,
    private val quranDao: QuranDao,
    private val inMemorySearchService: InMemorySearchService,
) : ViewModel() {

    val recentSearches: StateFlow<List<RecentSearchQuery>> =
        getRecentSearchQueriesUseCase(limit = 20)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Fast path: surahs, Quranic duas, popular verses, and Fortress chapter
     * titles — all tokenized + ranked in-memory. Runs at 50ms debounce so
     * results materialise almost as fast as the user types.
     */
    val inMemoryResults: StateFlow<InMemorySearchResult> =
        _searchQuery
            .debounce(50)
            .flatMapLatest { query ->
                val trimmed = query.trim()
                if (trimmed.length < SEARCH_QUERY_MIN_LENGTH) {
                    flowOf(InMemorySearchResult())
                } else {
                    flow {
                        val t0 = System.nanoTime()
                        val r = inMemorySearchService.search(trimmed, limitPerSource = 5)
                        android.util.Log.d(
                            "SearchPerf",
                            "VM inMemory emit('$trimmed') after ${(System.nanoTime() - t0) / 1_000_000}ms",
                        )
                        emit(r)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = InMemorySearchResult(),
            )

    /** Topics + News FTS, unchanged. SQL-backed, 120ms debounce. */
    val searchResults: StateFlow<UserSearchResult> =
        _searchQuery
            .debounce(120)
            .flatMapLatest { query ->
                val trimmed = query.trim()
                if (trimmed.length < SEARCH_QUERY_MIN_LENGTH) {
                    flowOf(UserSearchResult())
                } else {
                    getSearchContentsUseCase(trimmed)
                        .catch { emit(UserSearchResult()) }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UserSearchResult(),
            )

    /**
     * Ayah text search — every tokenized word must appear in the ayah text via
     * a multi-LIKE AND. Falls back to the raw trimmed query if tokenization
     * yields nothing (1-char queries, all-stop-word queries) so users still
     * get something.
     */
    val ayahResults: StateFlow<List<AyahEntity>> =
        _searchQuery
            .debounce(120)
            .flatMapLatest { query ->
                val trimmed = query.trim()
                if (trimmed.length < SEARCH_QUERY_MIN_LENGTH) {
                    flowOf(emptyList())
                } else {
                    flow {
                        val tokens = pickQueryTokens(trimmed)
                        if (tokens.isEmpty()) {
                            emit(emptyList())
                            return@flow
                        }
                        val t0 = System.nanoTime()
                        val result = runCatching {
                            quranDao.searchAyahsMultiToken(
                                t0 = tokens[0],
                                t1 = tokens.getOrElse(1) { "" },
                                t2 = tokens.getOrElse(2) { "" },
                                limit = 12,
                            )
                        }.getOrDefault(emptyList())
                        android.util.Log.d(
                            "SearchPerf",
                            "VM ayah emit('$trimmed') sql=${(System.nanoTime() - t0) / 1_000_000}ms n=${result.size}",
                        )
                        emit(result)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Fortress of the Muslim invocation search — multi-token AND across arabic,
     * transliteration, translation, context AND the chapter title via JOIN.
     * Chapter-title hits float to the top so typing "anxiety" surfaces the
     * Distress & Anxiety chapter's invocations first.
     */
    val fortressDuaResults: StateFlow<List<Dua>> =
        _searchQuery
            .debounce(120)
            .flatMapLatest { query ->
                val trimmed = query.trim()
                if (trimmed.length < SEARCH_QUERY_MIN_LENGTH) {
                    flowOf(emptyList())
                } else {
                    flow {
                        val tokens = pickQueryTokens(trimmed)
                        if (tokens.isEmpty()) {
                            emit(emptyList())
                            return@flow
                        }
                        val t0 = System.nanoTime()
                        val result = runCatching {
                            duaRepository.searchDuasMultiToken(tokens, limit = 20)
                        }.getOrDefault(emptyList())
                        android.util.Log.d(
                            "SearchPerf",
                            "VM fortress emit('$trimmed') sql=${(System.nanoTime() - t0) / 1_000_000}ms n=${result.size}",
                        )
                        emit(result)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            recentSearchRepository.insertOrReplaceRecentSearch(query.trim())
        }
    }

    /**
     * Return the up-to-3 most informative tokens for an SQL multi-LIKE query.
     * Stop words are dropped; if every word is a stop word, keep the longest
     * so a bare "dua" / "surah" still searches.
     */
    private fun pickQueryTokens(trimmed: String): List<String> {
        val tokens = SearchTokenizer.tokenize(trimmed)
        if (tokens.isNotEmpty()) return tokens.take(3)
        return listOf(trimmed.lowercase()).filter { it.length >= SEARCH_QUERY_MIN_LENGTH }
    }

    private companion object {
        const val SEARCH_QUERY_MIN_LENGTH = 2
    }
}
