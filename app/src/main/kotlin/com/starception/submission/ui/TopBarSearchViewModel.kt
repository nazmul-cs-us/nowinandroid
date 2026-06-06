package com.starception.submission.ui

import android.content.Context
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
import com.starception.submission.core.qurandatabase.SurahEntity
import com.starception.submission.core.quranicduas.QuranicDuaDatabase
import com.starception.submission.core.quranicduas.QuranicDuaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Combined dua search hit. [fortressDuas] are individual invocations from
 * fortress_of_the_muslim DB; [quranicDuas] are the 40 Quranic Duas table rows.
 * Both are searched in parallel with the FTS query.
 */
data class DuaSearchResult(
    val fortressDuas: List<Dua> = emptyList(),
    val quranicDuas: List<QuranicDuaEntity> = emptyList(),
)

/** Quran FTS hits — ayahs whose translation/text matches plus matching surah names. */
data class QuranSearchResult(
    val surahs: List<SurahEntity> = emptyList(),
    val ayahs: List<AyahEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TopBarSearchViewModel @Inject constructor(
    getRecentSearchQueriesUseCase: GetRecentSearchQueriesUseCase,
    private val recentSearchRepository: RecentSearchRepository,
    getSearchContentsUseCase: GetSearchContentsUseCase,
    private val duaRepository: DuaRepository,
    private val quranDao: QuranDao,
    @ApplicationContext appContext: Context,
) : ViewModel() {

    private val quranicDuaDao = QuranicDuaDatabase.getInstance(appContext).quranicDuaDao()

    val recentSearches: StateFlow<List<RecentSearchQuery>> =
        getRecentSearchQueriesUseCase(limit = 20)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
     * Ayah + surah search via QuranDao (LIKE on the active translation DB so
     * English keywords like "patience" surface verses, plus surah name matches).
     */
    val quranResults: StateFlow<QuranSearchResult> =
        _searchQuery
            .debounce(120)
            .flatMapLatest { query ->
                val trimmed = query.trim()
                if (trimmed.length < SEARCH_QUERY_MIN_LENGTH) {
                    flowOf(QuranSearchResult())
                } else {
                    flow {
                        val ayahs = runCatching {
                            quranDao.searchAyahsWithLimit(trimmed, 12)
                        }.getOrDefault(emptyList())
                        val surahs = runCatching {
                            quranDao.searchSurahs(trimmed).first()
                        }.getOrDefault(emptyList())
                        emit(QuranSearchResult(surahs = surahs.take(5), ayahs = ayahs))
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = QuranSearchResult(),
            )

    /**
     * Parallel dua search across the two SQLite-backed dua DBs (fortress + quranic).
     * Both DAOs already implement LIKE-based search, so we just call them on IO.
     */
    val duaResults: StateFlow<DuaSearchResult> =
        _searchQuery
            .debounce(120)
            .flatMapLatest { query ->
                val trimmed = query.trim()
                if (trimmed.length < SEARCH_QUERY_MIN_LENGTH) {
                    flowOf(DuaSearchResult())
                } else {
                    flow {
                        val fortress = runCatching {
                            duaRepository.searchDuas(query = trimmed, limit = 20)
                        }.getOrDefault(emptyList())
                        val quranic = runCatching {
                            quranicDuaDao.searchQuranicDuas(trimmed)
                        }.getOrDefault(emptyList())
                        emit(DuaSearchResult(fortressDuas = fortress, quranicDuas = quranic))
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DuaSearchResult(),
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

    private companion object {
        const val SEARCH_QUERY_MIN_LENGTH = 2
    }
}
