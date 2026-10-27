package com.poolaim.promax.ui.playergroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poolaim.promax.data.settings.SettingsRepository
import com.poolaim.promax.domain.model.PlayerGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for player group selection: exposes the saved group and
 * persists the user's choice via DataStore.
 */
class PlayerGroupViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val savedGroup: StateFlow<PlayerGroup?> = settingsRepository.playerGroup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedGroup = MutableStateFlow<PlayerGroup?>(null)
    val selectedGroup: StateFlow<PlayerGroup?> = _selectedGroup

    fun select(group: PlayerGroup) {
        _selectedGroup.value = group
    }

    fun confirmAndSave() {
        val group = _selectedGroup.value ?: return
        viewModelScope.launch {
            settingsRepository.setPlayerGroup(group)
        }
    }
}
