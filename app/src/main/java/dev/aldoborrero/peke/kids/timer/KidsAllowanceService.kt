package dev.aldoborrero.peke.kids.timer

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.services.KeyValueService
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
 * When a cached status was fetched, by [MonotonicClock], so the picker counts a stop down without the wall clock.
 * Cached next to the status; a status cached before it was has none.
 */
@Serializable
data class KidsFetchClock(
    val elapsedMillis: Long,
    val bootCount: Int? = null,
)

/**
 * A kid's last known status, whether it came from the server just now, and the time since it was fetched by
 * [MonotonicClock], null if that can't be told
 */
data class KnownKidsStatus(
    val status: KidsStatus,
    val fresh: Boolean,
    val sinceFetch: Duration?,
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
            when (val result = kidsApi.status(current.server.url, KidsCaller(token, current.user.id), current.user.id)) {
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
            cache(rowId, new)
        }

        /**
         * Cache [status] for the kid of users row [rowId], with when it was fetched, in one write so the two match
         */
        private suspend fun cache(
            rowId: Int,
            status: KidsStatus,
        ) {
            val fetched = KidsFetchClock(clock.elapsedMillis(), clock.bootCount())
            val json = keyValueService.json
            keyValueService.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    set(stringPreferencesKey(key(rowId)), json.encodeToString(status))
                    set(stringPreferencesKey(clockKey(rowId)), json.encodeToString(fetched))
                }
            }
        }

        /**
         * The last status cached for the kid of users row [rowId], current or not, or null if there is none or it
         * can't be read
         */
        private suspend fun cachedStatus(rowId: Int): KnownKidsStatus? =
            try {
                keyValueService.get<KidsStatus>(key(rowId)).first()?.let { status ->
                    val fetched = keyValueService.get<KidsFetchClock>(clockKey(rowId)).first()
                    KnownKidsStatus(status, fresh = false, sinceFetch(fetched, clock.elapsedMillis(), clock.bootCount()))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the cached kids status of %s", rowId)
                null
            }

        /**
         * [kid]'s status for the picker's rule on a stopped kid (spec FR-012a, research R6): asked with their own token
         * when the server answers within [timeoutMillis], and then cached, or else the last one cached. Null when
         * neither can tell, which the rule counts as stopped.
         *
         * @return the status, whether it came from the server just now, and the time since it was fetched
         */
        suspend fun lastKnownStatus(
            server: JellyfinServer,
            kid: JellyfinUser,
            timeoutMillis: Long = FRESH_STATUS_TIMEOUT_MILLIS,
        ): KnownKidsStatus? {
            val token = kid.accessToken
            if (token != null && pluginService.state(server.id).status == KidsServerStatus.AVAILABLE) {
                val result = withTimeoutOrNull(timeoutMillis) { kidsApi.status(server.url, KidsCaller(token, kid.id), kid.id) }
                if (result is KidsResult.Ok) {
                    try {
                        cache(kid.rowId, result.value)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w("Could not cache the kids status: %s", ex.javaClass.simpleName)
                    }
                    return KnownKidsStatus(result.value, fresh = true, sinceFetch = Duration.ZERO)
                }
                Timber.d("No fresh kids status for %s: %s", kid.rowId, result)
            }
            return cachedStatus(kid.rowId)
        }

        /**
         * Forget the cached status of the kids of users rows [rowIds], removed from this TV
         */
        suspend fun forget(rowIds: Collection<Int>) {
            try {
                keyValueService.remove(rowIds.flatMap { listOf(key(it), clockKey(it)) })
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not forget the cached kids status")
            }
        }

        /**
         * Add [minutes] to the current kid's allowance for today. Needs the parent grant.
         */
        suspend fun addBonus(minutes: Int): BonusResult {
            val auth = parentLockService.auth() ?: return BonusResult.NEEDS_GRANT
            val ticket = tickets.incrementAndGet()
            return when (val result = kidsApi.bonus(auth.server.url, auth.caller, auth.grant, auth.user.id, minutes)) {
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

            /**
             * How long the picker waits for the last kid's status from the server before it decides from the cache
             */
            const val FRESH_STATUS_TIMEOUT_MILLIS = 3_000L
            val SHORT_POLL_INTERVAL = 10.seconds
            private val SHORT_POLL_BELOW = 120.seconds

            private fun key(rowId: Int) = "kids_status_$rowId"

            private fun clockKey(rowId: Int) = "kids_status_${rowId}_clock"

            /**
             * The time since a status was fetched at [fetched], by [MonotonicClock] now at [elapsedNow] in boot
             * [bootNow], or null if it can't be told: no fetch time, as cached before it was kept, or a reboot since,
             * which restarts the clock
             */
            fun sinceFetch(
                fetched: KidsFetchClock?,
                elapsedNow: Long,
                bootNow: Int?,
            ): Duration? {
                if (fetched == null || fetched.bootCount != bootNow || elapsedNow < fetched.elapsedMillis) return null
                return (elapsedNow - fetched.elapsedMillis).milliseconds
            }

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
