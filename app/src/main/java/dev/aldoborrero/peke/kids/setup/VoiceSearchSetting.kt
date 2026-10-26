package dev.aldoborrero.peke.kids.setup

import androidx.datastore.core.DataStore
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.updateSearchPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This TV's voice search for kids (spec 005 FR-006 to FR-009): off until a grown-up turns it on behind the parent gate,
 * after reading where the voice goes and allowing the microphone themselves. Kids never answer the permission prompt.
 */
@Singleton
class VoiceSearchSetting
    @Inject
    constructor(
        private val preferences: DataStore<AppPreferences>,
        private val kidProfileDao: KidProfileDao,
    ) {
        /**
         * Whether a grown-up turned voice search on; an unreadable setting counts as off
         */
        val on: Flow<Boolean> =
            preferences.data
                .map { it.interfacePreferences.searchPreferences.kidsVoiceSearch }
                .catch {
                    Timber.w("Voice search setting unreadable: %s", it.javaClass.simpleName)
                    emit(false)
                }

        suspend fun isOn(): Boolean = on.first()

        suspend fun set(on: Boolean) {
            preferences.updateData { it.updateSearchPreferences { kidsVoiceSearch = on } }
        }

        /**
         * At the end of a first setup: offer voice search when it is off and one of this TV's kids may search
         */
        suspend fun offerAfterSetup(): Boolean = !isOn() && kidProfileDao.allUserIds().any { kidProfileDao.get(it)?.searchAllowed == true }
    }
