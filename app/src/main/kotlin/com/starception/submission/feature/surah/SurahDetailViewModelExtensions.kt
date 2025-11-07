package com.starception.submission.feature.surah

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

fun SurahDetailViewModel.uiStateLiveData(): LiveData<SurahDetailUiState> =
    uiState.asLiveData()

