package dev.aldoborrero.peke.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import dev.aldoborrero.peke.BuildConfig
import dev.aldoborrero.peke.data.SeerrServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.SeerrAuthMethod
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Listens for JF user switching in the app to also switch other settings like Seerr user/server
 */
@ActivityScoped
class UserSwitchListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrServerDao: SeerrServerDao,
        private val seerrApi: SeerrApi,
        private val homeSettingsService: HomeSettingsService,
        private val profilePolicy: ProfilePolicy,
    ) {
        init {
            context as AppCompatActivity
            context.lifecycleScope.launchDefault {
                serverRepository.currentUserFlow
                    .distinctUntilChanged { old, new ->
                        old?.id == new?.id && old?.uiLanguage == new?.uiLanguage
                    }.collectLatest { user ->
                        Timber.d("UserSwitch: %s", user?.id)
                        seerrServerRepository.clear()
                        homeSettingsService.currentSettings.update { HomePageResolvedSettings.EMPTY }
                        if (user != null) {
                            switchUser(user)
                        }
                    }
            }
            // A fixed home, such as a kid's, can change after the switch above, so rebuild it then
            context.lifecycleScope.launchDefault {
                profilePolicy.homeChanges.collectLatest {
                    serverRepository.currentUser?.let {
                        Timber.d("Profile changed: rebuilding the home of %s", it.id)
                        homeSettingsService.loadCurrentSettings(it.id)
                    }
                }
            }
        }

        private suspend fun switchUser(user: JellyfinUser) =
            supervisorScope {
                // Switch the locale to either the user's choice or the system default (empty)
                val localeList =
                    user.uiLanguage?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList()
                Timber.i("Switching locale to %s", localeList)
                withContext(PekeDispatchers.Main) {
                    AppCompatDelegate.setApplicationLocales(localeList)
                }

                // Check for home settings
                launchIO {
                    homeSettingsService.loadCurrentSettings(user.id)
                }
                // Some profiles, such as kids, never use Seerr, so discover and requests stay hidden
                if (BuildConfig.DISCOVER_ENABLED && profilePolicy.allowsDiscover(user)) {
                    // Check for seerr server
                    launchIO {
                        seerrServerDao
                            .getUsersByJellyfinUser(user.rowId)
                            .lastOrNull()
                            ?.let { seerrUser ->
                                val server =
                                    seerrServerDao.getServer(seerrUser.serverId)?.server
                                if (server != null) {
                                    Timber.i("Found a seerr user & server")
                                    try {
                                        seerrApi.update(server.url, seerrUser.credential)
                                        val userConfig =
                                            if (seerrUser.authMethod != SeerrAuthMethod.API_KEY) {
                                                seerrLogin(
                                                    seerrApi.api,
                                                    seerrUser.authMethod,
                                                    seerrUser.username,
                                                    seerrUser.password,
                                                )
                                            } else {
                                                seerrApi.api.usersApi.authMeGet()
                                            }
                                        seerrServerRepository.set(
                                            server,
                                            seerrUser,
                                            userConfig,
                                        )
                                    } catch (ex: Exception) {
                                        Timber.w(
                                            ex,
                                            "Error logging into %s",
                                            server.url,
                                        )
                                        seerrServerRepository.error(server, seerrUser, ex)
                                    }
                                }
                            }
                    }
                }
            }
    }
