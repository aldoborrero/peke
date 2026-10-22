package com.github.damontecres.wholphin.kids.timer

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.damontecres.wholphin.kids.KidsModeService
import com.github.damontecres.wholphin.kids.isActive
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Music has its own player, which the video playback lifecycle doesn't pause. In kids mode it stops when the server
 * denies (time's up, bedtime or a break), and pauses when the app goes to the background, where nobody watches it.
 */
@Singleton
class KidsMusicGuard
    @Inject
    constructor(
        private val kidsModeService: KidsModeService,
        private val musicService: MusicService,
        timeLimits: KidsTimeLimits,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) : DefaultLifecycleObserver {
        init {
            combine(kidsModeService.state, timeLimits.state) { kids, limits -> kids.isActive && limits.expired }
                .distinctUntilChanged()
                .filter { it }
                .onEach {
                    Timber.i("Time's up: stopping music")
                    musicService.stopIfStarted()
                }.launchIn(scope)
        }

        override fun onPause(owner: LifecycleOwner) {
            if (kidsModeService.isActive) scope.launch { musicService.pauseIfStarted() }
        }
    }
