package dev.aldoborrero.peke.kids.timer

import dev.aldoborrero.peke.services.KeyValueService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the kids timer's remaining-time bar is shown
 */
enum class KidsTimerBarPosition {
    TOP,
    BOTTOM,
    HIDDEN,
}

/**
 * Where the time-left label sits along the bar, in the layout's direction (start is the left in English and Spanish)
 */
enum class KidsTimerLabelPlacement {
    START,
    CENTER,
    END,
}

/**
 * Device-wide setting for the [KidsTimerBarPosition] and the [KidsTimerLabelPlacement]
 */
@Singleton
class KidsTimerBarSetting
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
    ) {
        val position: Flow<KidsTimerBarPosition> = keyValueService.get(KEY, KidsTimerBarPosition.TOP)

        /**
         * Kept apart from [position], so a stored position reads as before; centred, as the label always was
         */
        val placement: Flow<KidsTimerLabelPlacement> = keyValueService.get(PLACEMENT_KEY, KidsTimerLabelPlacement.CENTER)

        suspend fun set(position: KidsTimerBarPosition) = keyValueService.save(KEY, position)

        suspend fun setPlacement(placement: KidsTimerLabelPlacement) = keyValueService.save(PLACEMENT_KEY, placement)

        companion object {
            internal const val KEY = "kids_timer_bar"
            internal const val PLACEMENT_KEY = "kids_timer_label"
        }
    }
