package com.github.damontecres.wholphin.kids.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/**
 * How the Kids plugin refuses a denied kid's playback when server enforcement is on: `PlaybackInfo` answers with
 * `ErrorCode` `NotAllowed`, and stream routes with a 403 carrying `X-Application-Error-Code: PlaybackNotAllowed`. Both
 * give the reason in `X-Kids-Reason`.
 */
object KidsPlaybackRefusal {
    const val ERROR_CODE_HEADER = "X-Application-Error-Code"
    const val PLAYBACK_NOT_ALLOWED = "PlaybackNotAllowed"
    const val REASON_HEADER = "X-Kids-Reason"

    /**
     * A live TV channel (or program, or stream) outside the kid's channels
     */
    const val LIVE_TV_CHANNEL = "liveTvChannel"

    /**
     * Live TV recordings and timers, which kids never have
     */
    const val LIVE_TV_RECORDING = "liveTvRecording"

    enum class Kind {
        /**
         * "This channel isn't available", with no retry and no playback error
         */
        LIVE_TV_CHANNEL,

        /**
         * Recordings are hidden in kids mode; one reached anyway is left with "This isn't available"
         */
        LIVE_TV_RECORDING,

        /**
         * A time rule, or no reason: the time limits decide which kids screen shows
         */
        TIME,
    }

    /**
     * What a refusal with [reason] is about
     */
    fun kindOf(reason: String?): Kind =
        when {
            reason?.trim().equals(LIVE_TV_CHANNEL, ignoreCase = true) -> Kind.LIVE_TV_CHANNEL
            reason?.trim().equals(LIVE_TV_RECORDING, ignoreCase = true) -> Kind.LIVE_TV_RECORDING
            else -> Kind.TIME
        }

    /**
     * A refused playback, with the plugin's reason if it gave one
     */
    data class Refusal(
        val reason: String?,
    )

    /**
     * The value of header [name] in [headers], whatever its case
     */
    fun header(
        headers: Map<out String?, List<String>>,
        name: String,
    ): String? =
        headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()

    /**
     * The plugin's refusal of a stream, found in a player [error] and its causes, or null if it is another error
     */
    @OptIn(UnstableApi::class)
    fun of(error: Throwable): Refusal? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                if (cause.responseCode != 403) return null
                val headers = cause.headerFields
                if (!header(headers, ERROR_CODE_HEADER).equals(PLAYBACK_NOT_ALLOWED, ignoreCase = true)) return null
                return Refusal(header(headers, REASON_HEADER))
            }
            cause = cause.cause
        }
        return null
    }
}
