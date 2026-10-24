package dev.aldoborrero.peke.services

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.KidsServerMessages
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.ui.collectLatestIn
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.UserUpdatedMessage
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Listens for basic messages from the server such as messages
 */
@ActivityScoped
class ServerEventListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val kidsModeService: KidsModeService,
        private val kidsAllowanceService: KidsAllowanceService,
    ) : DefaultLifecycleObserver {
        private val activity = (context as AppCompatActivity)

        private var listenJob: Job? = null

        init {
            activity.lifecycle.addObserver(this)
            serverRepository.current.collectLatestIn(activity.lifecycleScope) {
                Timber.d("New user/server: %s", it)
                listenJob?.cancel()
                if (it != null) {
                    init(it.server, it.user)
                }
            }
        }

        fun init(
            server: JellyfinServer?,
            user: JellyfinUser?,
        ) {
            if (server != null && user != null && api.baseUrl != null && api.accessToken != null) {
                (context as AppCompatActivity).lifecycleScope.launchIO {
                    api.sessionApi.postCapabilities(
                        playableMediaTypes = listOf(MediaType.VIDEO),
                        supportedCommands =
                            listOf(
                                GeneralCommandType.DISPLAY_MESSAGE,
                                GeneralCommandType.SEND_STRING,
                            ),
                        supportsMediaControl = true,
                    )
                    subscribeToWebSocket()
                }
            }
        }

        fun subscribeToWebSocket() {
            Timber.v("Subscribing to WebSocket")
            listenJob?.cancel()
            listenJob =
                activity.lifecycleScope.launchDefault {
                    try {
                        // Launch multiple listeners, but stop all if one fails
                        coroutineScope {
                            api.webSocket
                                .subscribe<GeneralCommandMessage>()
                                .onEach { message ->
                                    Timber.v(
                                        "Got GeneralCommandMessage: %s",
                                        message.data?.name,
                                    )
                                    when (message.data?.name) {
                                        GeneralCommandType.DISPLAY_MESSAGE,
                                        GeneralCommandType.SEND_STRING,
                                        -> {
                                            val header = message.data?.arguments["Header"]
                                            val text =
                                                message.data?.arguments["Text"]
                                                    ?: message.data?.arguments["String"]
                                            val toast =
                                                listOfNotNull(header, text)
                                                    .joinToString("\n")
                                            if (toast.isNotBlank() && KidsServerMessages.shouldShow(header, hideForKids(header))) {
                                                showToast(context, toast, Toast.LENGTH_LONG)
                                            }
                                        }

                                        else -> {
                                            Timber.v(
                                                "Ignoring GeneralCommandMessage: %s",
                                                message.data?.name,
                                            )
                                        }
                                    }
                                }.catch { ex ->
                                    Timber.e(ex, "Error in general message websocket subscription")
                                }.launchIn(this@coroutineScope)

                            api.webSocket
                                .subscribe<UserUpdatedMessage>()
                                .catch { ex ->
                                    Timber.e(ex, "Error in user updated websocket subscription")
                                }.collectLatestIn(this@coroutineScope) { msg ->
                                    Timber.v("Got updated user: %s", msg.data?.id)
                                    msg.data?.let { serverRepository.updateUserDto(it) }
                                }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error in websocket connection")
                        if (activity.lifecycleScope.isActive) {
                            subscribeToWebSocket()
                        }
                    }
                }
        }

        /**
         * In kids mode, the Kids plugin's own warnings and denial messages are hidden when they repeat what kids mode
         * already shows. They are a sign the status changed, so it is read again first. Every other
         * account's message is dropped, see [KidsServerMessages.shouldShow].
         */
        private suspend fun hideForKids(header: String?): Boolean {
            if (!kidsModeService.isActive || !KidsServerMessages.isKids(header)) return false
            val limits = kidsAllowanceService.check()
            val warnAt =
                kidsAllowanceService.snapshot.value
                    ?.status
                    ?.warnAtSeconds
                    .orEmpty()
                    .map { it.seconds }
            return KidsServerMessages.shouldHide(header, limits, warnAt).also {
                if (it) Timber.d("Hiding the Kids plugin's message: %s", header)
            }
        }

        override fun onResume(owner: LifecycleOwner) {
            serverRepository.current.value?.let { init(it.server, it.user) }
        }

        override fun onPause(owner: LifecycleOwner) {
            Timber.v("Cancelling WebSocket")
            listenJob?.cancel()
        }

        override fun onStop(owner: LifecycleOwner) {
            Timber.v("Cancelling WebSocket")
            listenJob?.cancel()
        }
    }
