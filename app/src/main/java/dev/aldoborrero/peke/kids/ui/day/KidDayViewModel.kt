package dev.aldoborrero.peke.kids.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The kid's figures for today, in minutes, from the server's status
 *
 * @param allowanceMinutes today's allowance with time added, or null when the kid has no daily limit
 * @param leftMinutes what is left today, or null when the kid has no daily limit
 * @param sittingUsedMinutes what this sitting used, with the sitting limit only
 * @param breakLeftMinutes what is left of the break after a sitting, while the kid is on one
 */
data class KidDay(
    val usedMinutes: Int,
    val allowanceMinutes: Int?,
    val bonusMinutes: Int,
    val leftMinutes: Int?,
    val sittingUsedMinutes: Int?,
    val sittingLimitMinutes: Int?,
    val breakLeftMinutes: Int?,
) {
    companion object {
        fun from(status: KidsStatus): KidDay {
            val allowance = status.allowanceSeconds
            return KidDay(
                usedMinutes = minutes(status.usedSeconds),
                allowanceMinutes = allowance?.let { minutes(it + status.bonusSeconds) },
                bonusMinutes = minutes(status.bonusSeconds),
                leftMinutes =
                    allowance?.let {
                        val left = status.remainingSeconds ?: (it + status.bonusSeconds - status.usedSeconds)
                        minutesUp(left.coerceAtLeast(0))
                    },
                sittingUsedMinutes = status.sittingLimitSeconds?.let { minutes(status.sittingUsedSeconds ?: 0) },
                sittingLimitMinutes = status.sittingLimitSeconds?.let(::minutes),
                breakLeftMinutes = status.breakRemainingSeconds?.takeIf { it > 0 }?.let(::minutesUp),
            )
        }

        private fun minutes(seconds: Long) = (seconds / 60).toInt()

        /**
         * Rounded up, so a break or time with seconds left never reads 0
         */
        private fun minutesUp(seconds: Long) = ((seconds + 59) / 60).toInt()
    }
}

data class KidDayState(
    val kidId: UUID? = null,
    val kidName: String? = null,
    /**
     * Today's figures, or null before the server's first answer
     */
    val day: KidDay? = null,
    /**
     * Whether the last status request had an answer, null before the first
     */
    val reachable: Boolean? = null,
)

/**
 * "{Kid}'s day" (spec 030 US3): the current kid's use today, read only, apart from their rules. The status is asked for
 * again on opening, so the figures are fresh.
 */
@HiltViewModel
class KidDayViewModel
    @Inject
    constructor(
        serverRepository: ServerRepository,
        private val allowanceService: KidsAllowanceService,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                serverRepository.current.value
                    ?.user
                    .let { KidDayState(kidId = it?.id, kidName = it?.name) },
            )
        val state: StateFlow<KidDayState> = _state

        init {
            allowanceService.refresh()
            viewModelScope.launch {
                allowanceService.snapshot.collect { snapshot ->
                    _state.update { it.copy(day = snapshot?.status?.let(KidDay::from)) }
                }
            }
            viewModelScope.launch {
                allowanceService.reachable.collect { reachable -> _state.update { it.copy(reachable = reachable) } }
            }
        }
    }
