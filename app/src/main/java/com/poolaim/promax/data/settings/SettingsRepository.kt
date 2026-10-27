package com.poolaim.promax.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.poolaim.promax.domain.model.PlayerGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "poolaim_settings")

/**
 * Repository for lightweight app settings backed by DataStore Preferences.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PLAYER_GROUP = stringPreferencesKey("player_group")
    }

    val playerGroup: Flow<PlayerGroup?> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.PLAYER_GROUP]?.let { runCatching { PlayerGroup.valueOf(it) }.getOrNull() } }

    suspend fun setPlayerGroup(group: PlayerGroup) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.PLAYER_GROUP] = group.name }
    }
}
