package dev.aldoborrero.peke.services

import androidx.datastore.core.DataStore
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Get the current user's [UserPreferences]
 */
@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
    ) {
        val flow: Flow<UserPreferences> =
            preferencesDataStore.data.combine(serverRepository.currentUserFlow) { appPrefs, user ->
                UserPreferences(appPrefs, user?.appPreferences)
            }

        suspend fun getCurrent(): UserPreferences = flow.first()
    }
