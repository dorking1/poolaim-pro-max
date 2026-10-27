package com.poolaim.promax.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poolaim.promax.data.local.AnalysisSessionEntity
import com.poolaim.promax.data.local.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase F ViewModel: exposes the recent analysis history.
 */
class HistoryViewModel(
    private val repository: HistoryRepository,
) : ViewModel() {

    val sessions: StateFlow<List<AnalysisSessionEntity>> = repository.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
