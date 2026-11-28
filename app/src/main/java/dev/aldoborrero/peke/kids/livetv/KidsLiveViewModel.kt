package dev.aldoborrero.peke.kids.livetv

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.services.NavigationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * The kid's channels on the home's Watch live row, to show the ones with nothing airing (spec 036)
 */
@HiltViewModel
class KidsLiveChannelsViewModel
    @Inject
    constructor(
        liveTv: KidsLiveTvService,
    ) : ViewModel() {
        val channels: StateFlow<List<KidsChannel>> = liveTv.channels
    }

/**
 * What the Live page shows: the kid's channels and their guide for the next hours
 *
 * @param guide null until the first read answered; an empty guide after a failed first read, so the channels show
 * without one
 */
data class KidsLiveState(
    val channels: List<KidsChannel>,
    val guide: List<LiveProgramme>?,
)

/**
 * The Live page (spec 036): the kid's channels from [KidsLiveTvService], and one read of the plugin's guide for
 * [LiveGuideWindow.LENGTH], read again only when the page runs out of a "later" for a channel. Now and next are worked
 * out on the TV's clock, with no requests in between.
 */
@HiltViewModel
class KidsLiveViewModel
    @Inject
    constructor(
        private val liveTv: KidsLiveTvService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val guide = MutableStateFlow<List<LiveProgramme>?>(null)

        val state: StateFlow<KidsLiveState> =
            combine(liveTv.channels, guide, ::KidsLiveState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, KidsLiveState(liveTv.channels.value, null))

        /** The TV's clock; tests set a virtual one */
        @VisibleForTesting
        internal var clock: () -> Instant = { Instant.now() }

        @VisibleForTesting
        internal var zone: ZoneId = ZoneId.systemDefault()

        private var job: Job? = null

        /** Reads the guide while the page is shown */
        fun start() {
            if (job?.isActive == true) return
            job = viewModelScope.launch { readGuide() }
        }

        /** The page was left: no more reads until it is shown again */
        fun stop() {
            job?.cancel()
            job = null
        }

        private suspend fun readGuide() {
            while (true) {
                val read = liveTv.guide(clock(), zone)
                if (read != null) {
                    guide.value = read
                } else if (guide.value == null) {
                    // The channels show without a guide rather than an error; the next read may bring it
                    guide.value = emptyList()
                }
                delay(nextRead(read, guide.value.orEmpty(), LocalDateTime.ofInstant(clock(), zone)) ?: return)
            }
        }

        companion object {
            /** After a failed read */
            val RETRY = 1.minutes

            /** Past the moment the guide runs out, so the server has the next programme */
            val GRACE = 2.seconds

            /**
             * How long until the guide is read again: [RETRY] after a failed read, otherwise [GRACE] past
             * [guideRefreshAt]; never sooner than [RETRY]'s quarter, so a clock behind the server can't make a busy
             * loop. Null with nothing to wait for.
             */
            fun nextRead(
                read: List<LiveProgramme>?,
                guide: List<LiveProgramme>,
                now: LocalDateTime,
            ): Duration? {
                if (read == null) return RETRY
                val at = guideRefreshAt(guide, now) ?: return null
                val wait =
                    java.time.Duration
                        .between(now, at)
                        .toKotlinDuration() + GRACE
                return maxOf(wait, RETRY / 4)
            }
        }
    }
