package dev.aldoborrero.peke.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Listens for JF user switching in the app to also switch other settings like the locale and home
 */
@ActivityScoped
class UserSwitchListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val homeSettingsService: HomeSettingsService,
        private val profilePolicy: ProfilePolicy,
        private val itemHandOver: ItemHandOver,
    ) {
        /**
         * The user whose home settings to build. Conflated, and built by one collector that drops a build a newer
         * request makes stale: a kid's start asks three times in a moment (the switch, their profile, their channels).
         */
        private val rebuilds = Channel<UUID>(Channel.CONFLATED)

        init {
            context as AppCompatActivity
            context.lifecycleScope.launchDefault {
                serverRepository.currentUserFlow
                    .distinctUntilChanged { old, new ->
                        old?.id == new?.id && old?.uiLanguage == new?.uiLanguage
                    }.collectLatest { user ->
                        Timber.d("UserSwitch: %s", user?.id)
                        homeSettingsService.currentSettings.update { HomePageResolvedSettings.EMPTY }
                        itemHandOver.clear()
                        if (user != null) {
                            switchUser(user)
                        }
                    }
            }
            // A fixed home, such as a kid's, can change after the switch above, so rebuild it then
            context.lifecycleScope.launchDefault {
                profilePolicy.homeChanges.collect {
                    serverRepository.currentUser?.let {
                        Timber.d("Profile changed: rebuilding the home of %s", it.id)
                        rebuilds.send(it.id)
                    }
                }
            }
            context.lifecycleScope.launchIO {
                rebuilds.receiveAsFlow().collectLatest { userId ->
                    try {
                        homeSettingsService.loadCurrentSettings(userId)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Could not build the home of %s", userId)
                    }
                }
            }
        }

        private suspend fun switchUser(user: JellyfinUser) {
            // Switch the locale to either the user's choice or the system default (empty)
            val localeList =
                user.uiLanguage?.let { LocaleListCompat.forLanguageTags(it) }
                    ?: LocaleListCompat.getEmptyLocaleList()
            Timber.i("Switching locale to %s", localeList)
            withContext(PekeDispatchers.Main) {
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            // Check for home settings
            rebuilds.send(user.id)
        }
    }
