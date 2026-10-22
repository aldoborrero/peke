package dev.aldoborrero.peke.kids.timer

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.services.KeyValueService
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class BonusResult {
    ADDED,
    NEEDS_GRANT,
    UNREACHABLE,
    FAILED,
}

/**
 * A status and when it was received, in [MonotonicClock] millis, to count down from between polls
 */
data class KidsSnapshot(
    val status: KidsStatus,
    val receivedAt: Long,
)

/**
 * The current kid's time, as kept by the Jellyfin Kids plugin across every client: the daily allowance, the sitting
 * and its break, and whether the server denies playback. The app keeps no time of its own.
 *
 * The status is polled while a kid is current and the app is in the foreground, and cached per kid so that a denial
 * survives restarts and holds while the server can't be reached.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class KidsAllowanceService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val kidsModeService: KidsModeService,
        private val pluginService: KidsPluginService,
        private val parentLockService: ParentLockService,
        private val kidsApi: KidsApi,
        private val keyValueService: KeyValueService,
        private val clock: MonotonicClock,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) : DefaultLifecycleObserver {
        private val _snapshot = MutableStateFlow<KidsSnapshot?>(null)

        /**
         * The current kid's last known status, or null when none applies
         */
        val snapshot: StateFlow<KidsSnapshot?> = _snapshot

        private val _refusal = MutableStateFlow<KidsDenial?>(null)

        /**
         * Why the server refused a playback since the last status, until a status asked for after it replaces it
         */
        val refusal: StateFlow<KidsDenial?> = _refusal

        private val _foreground = MutableStateFlow(false)
        val foreground: StateFlow<Boolean> = _foreground

        private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

        private val mutex = Mutex()
        private val tickets = AtomicLong()

        /**
         * The kid [snapshot] is about, and the ticket of the request it came from (0 for the cache)
         */
        private var statusRowId: Int? = null
        private var lastApplied = 0L

        /**
         * The last ticket taken before [refusal], so only a status asked for after it clears it
         */
        private var refusalTicket = 0L

        init {
            scope.launch {
                combine(kidsModeService.state, pluginService.current, _foreground) { kids, plugin, fg ->
                    Triple((kids as? KidsModeState.Active)?.user?.rowId, plugin.status, fg && plugin.has(KidsFeatures.ALLOWANCE))
                }.distinctUntilChanged()
                    .collectLatest { (rowId, pluginStatus, poll) ->
                        if (rowId == null) {
                            mutex.withLock {
                                statusRowId = null
                                _snapshot.value = null
                                _refusal.value = null
                            }
                            return@collectLatest
                        }
                        val cached = keyValueService.get<KidsStatus>(key(rowId)).first()
                        mutex.withLock {
                            if (statusRowId != rowId) {
                                statusRowId = rowId
                                lastApplied = 0
                                _refusal.value = null
                                _snapshot.value = cached?.let { KidsSnapshot(it, clock.elapsedMillis()) }
                            }
                        }
                        if (!poll || pluginStatus != KidsServerStatus.AVAILABLE) return@collectLatest
                        while (true) {
                            fetch(rowId)
                            val limits = KidsLimits.of(_snapshot.value?.status, _refusal.value)
                            withTimeoutOrNull(interval(limits)) { refreshRequests.receive() }
                        }
                    }
            }
        }

        override fun onResume(owner: LifecycleOwner) {
            _foreground.value = true
        }

        override fun onPause(owner: LifecycleOwner) {
            _foreground.value = false
        }

        /**
         * Ask the server again now, for example after playback stopped
         */
        fun refresh() {
            refreshRequests.trySend(Unit)
        }

        /**
         * The server refused playback, with the `X-Kids-Reason` [reason] if it gave one
         *
         * @return whether a kids screen shows for it: always with a reason, otherwise if the status, asked for again
         * now, denies
         */
        suspend fun refused(reason: String?): Boolean {
            val denial = KidsDenial.of(reason)
            if (denial != null) {
                mutex.withLock {
                    if (statusRowId == null) return false
                    refusalTicket = tickets.get()
                    _refusal.value = denial
                }
                refresh()
                return true
            }
            // Jellyfin itself refuses outside allowed hours, without a reason: the status tells
            return check().expired
        }

        /**
         * Ask the server now and wait for the answer, or keep the last known status if it can't be reached
         *
         * @return the limits by that status, without counting down
         */
        suspend fun check(): KidsLimits {
            statusRowId?.let { fetch(it) }
            return KidsLimits.of(_snapshot.value?.status, _refusal.value)
        }

        private suspend fun fetch(rowId: Int) {
            val current = serverRepository.current.value ?: return
            if (current.user.rowId != rowId) return
            val token = current.user.accessToken ?: return
            val ticket = tickets.incrementAndGet()
            when (val result = kidsApi.status(current.server.url, token, current.user.id)) {
                is KidsResult.Ok -> update(rowId, result.value, ticket)

                // Not a kid any more, or the plugin went away: the profile refresh decides
                KidsResult.NotAKid, KidsResult.NotFound -> Unit

                else -> Timber.d("Keeping the cached kids status: %s", result)
            }
        }

        /**
         * Use [new] unless a newer status was already applied, such as a bonus answered while a poll was on its way
         *
         * @param ticket taken when the request was sent, so later requests have higher ones
         */
        private suspend fun update(
            rowId: Int,
            new: KidsStatus,
            ticket: Long,
        ) {
            mutex.withLock {
                // An answer for a kid who is no longer current
                if (rowId != statusRowId) return
                if (!isNewer(_snapshot.value?.status, new, ticket, lastApplied)) {
                    Timber.d("Ignoring an older kids status")
                    return
                }
                lastApplied = ticket
                if (ticket > refusalTicket) _refusal.value = null
                _snapshot.value = KidsSnapshot(new, clock.elapsedMillis())
            }
            keyValueService.save(key(rowId), new)
        }

        /**
         * Add [minutes] to the current kid's allowance for today. Needs the parent grant.
         */
        suspend fun addBonus(minutes: Int): BonusResult {
            val auth = parentLockService.auth() ?: return BonusResult.NEEDS_GRANT
            val ticket = tickets.incrementAndGet()
            return when (val result = kidsApi.bonus(auth.server.url, auth.token, auth.grant, auth.user.id, minutes)) {
                is KidsResult.Ok -> {
                    update(auth.user.rowId, result.value, ticket)
                    BonusResult.ADDED
                }

                KidsResult.Forbidden -> {
                    parentLockService.dropGrant()
                    BonusResult.NEEDS_GRANT
                }

                KidsResult.Unreachable -> {
                    BonusResult.UNREACHABLE
                }

                else -> {
                    Timber.w("Could not add time: %s", result)
                    BonusResult.FAILED
                }
            }
        }

        companion object {
            val POLL_INTERVAL = 60.seconds
            val SHORT_POLL_INTERVAL = 10.seconds
            private val SHORT_POLL_BELOW = 120.seconds

            private fun key(rowId: Int) = "kids_status_$rowId"

            /**
             * Poll more often when a warning or the end is close, or while denied, so the screens come and go on
             * time. A break about to end is asked about right after it ends.
             */
            fun interval(limits: KidsLimits): Duration {
                val breakLeft = limits.breakRemaining
                val remaining = limits.remaining
                return when {
                    limits.expired && breakLeft != null -> (breakLeft + 1.seconds).coerceIn(1.seconds, SHORT_POLL_INTERVAL)
                    limits.expired -> SHORT_POLL_INTERVAL
                    remaining != null && remaining <= SHORT_POLL_BELOW -> SHORT_POLL_INTERVAL
                    else -> POLL_INTERVAL
                }
            }

            /**
             * Whether [new] may replace [previous]: the status the server computed later, by `ServerTime`, or without
             * it the answer to the request sent later. A status from the cache ([lastApplied] 0) is always replaced,
             * so a server clock that went back can't keep an old time's up.
             */
            fun isNewer(
                previous: KidsStatus?,
                new: KidsStatus,
                ticket: Long,
                lastApplied: Long,
            ): Boolean {
                if (previous == null || lastApplied == 0L) return true
                val before = previous.serverTime?.let(::parseInstant)
                val after = new.serverTime?.let(::parseInstant)
                return if (before != null && after != null) !after.isBefore(before) else ticket > lastApplied
            }

            private fun parseInstant(value: String): Instant? =
                try {
                    OffsetDateTime.parse(value).toInstant()
                } catch (_: Exception) {
                    try {
                        Instant.parse(value)
                    } catch (_: Exception) {
                        null
                    }
                }
        }
    }
