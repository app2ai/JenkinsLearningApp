package com.example.jenkinslearning.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.SessionCredentials
import com.example.jenkinslearning.domain.repository.ConnectionSettingsRepository
import com.example.jenkinslearning.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persists NON-SECRET connection settings with Preferences DataStore.
 * DataStore files are unencrypted app-private storage, which is fine for a URL
 * and username but not for an API token. The token never comes here.
 *
 * Until the user saves something, the URL defaults to BuildConfig's value,
 * which came from local.properties / JENKINS_BASE_URL at build time.
 */
class DataStoreConnectionSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaultBaseUrl: String,
) : ConnectionSettingsRepository {

    override val settings: Flow<ConnectionSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            ConnectionSettings(
                baseUrl = prefs[KEY_BASE_URL] ?: defaultBaseUrl,
                username = prefs[KEY_USERNAME].orEmpty(),
                mode = prefs[KEY_MODE]?.let { stored -> DataMode.entries.firstOrNull { it.name == stored } } ?: DataMode.SAMPLE,
            )
        }

    override suspend fun save(settings: ConnectionSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = settings.baseUrl
            prefs[KEY_USERNAME] = settings.username
            prefs[KEY_MODE] = settings.mode.name
        }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("jenkins_base_url")
        val KEY_USERNAME = stringPreferencesKey("jenkins_username")
        val KEY_MODE = stringPreferencesKey("data_mode")
    }
}

/**
 * Holds the API token in memory for the lifetime of the app process.
 * Closing the app (or Android killing the process) forgets it, which is the
 * point: nothing secret is written to disk.
 */
class InMemorySessionRepository : SessionRepository {
    private val credentials = MutableStateFlow<SessionCredentials?>(null)
    private val _hasCredentials = MutableStateFlow(false)
    override val hasCredentials: StateFlow<Boolean> = _hasCredentials.asStateFlow()

    override fun current(): SessionCredentials? = credentials.value

    override fun set(credentials: SessionCredentials) {
        this.credentials.value = credentials
        _hasCredentials.value = true
    }

    override fun clear() {
        credentials.value = null
        _hasCredentials.value = false
    }
}
