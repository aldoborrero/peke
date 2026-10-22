package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.BuildConfig
import dev.aldoborrero.peke.api.seerr.SeerrApiClient
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.setup.seerr.createSeerrApiUrl
import okhttp3.OkHttpClient

/**
 * Wrapper for [SeerrApiClient]. In most cases, you should use [SeerrService] instead.
 */
class SeerrApi(
    private val okHttpClient: OkHttpClient,
) {
    var api: SeerrApiClient =
        SeerrApiClient(
            baseUrl = "",
            apiKey = null,
            okHttpClient = okHttpClient,
        )
        private set

    val active: Boolean get() = api.baseUrl.isNotNullOrBlank() && BuildConfig.DISCOVER_ENABLED

    fun update(
        baseUrl: String,
        apiKey: String?,
    ) {
        api = SeerrApiClient(createSeerrApiUrl(baseUrl), apiKey, okHttpClient)
    }
}
