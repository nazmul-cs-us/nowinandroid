package com.starception.submission.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.data.model.RecentSearchQuery
import com.starception.submission.core.data.repository.RecentSearchRepository
import com.starception.submission.core.domain.GetRecentSearchQueriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TopBarSearchViewModel @Inject constructor(
    getRecentSearchQueriesUseCase: GetRecentSearchQueriesUseCase,
    private val recentSearchRepository: RecentSearchRepository,
) : ViewModel() {

    val recentSearches: StateFlow<List<RecentSearchQuery>> =
        getRecentSearchQueriesUseCase(limit = 20)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            recentSearchRepository.insertOrReplaceRecentSearch(query.trim())
        }
    }
}
