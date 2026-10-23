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
 * Device-wide setting for the [KidsTimerBarPosition]
 */
@Singleton
class KidsTimerBarSetting
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
    ) {
        val position: Flow<KidsTimerBarPosition> = keyValueService.get(KEY, KidsTimerBarPosition.TOP)

        suspend fun set(position: KidsTimerBarPosition) = keyValueService.save(KEY, position)

        companion object {
            private const val KEY = "kids_timer_bar"
        }
    }
