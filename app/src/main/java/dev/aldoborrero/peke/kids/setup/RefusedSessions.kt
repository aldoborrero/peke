package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a session the server refused ends up (spec FR-019): every request of the app, through the Jellyfin SDK or
 * [dev.aldoborrero.peke.kids.server.KidsApi], goes through [RefusedTokenInterceptor], which hands the token of each
 * 401 answer here. The kid it belongs to then keeps no token, so the picker shows them as needing a grown-up, and if
 * they were current the kids side starts over from the launch rule.
 *
 * Only 401 counts: Jellyfin answers it for a token it doesn't know any more (signed out in the dashboard, password
 * changed, device removed). A 403 leaves the token alone: Jellyfin answers it for a kid outside their access schedule,
 * the Kids plugin for its bedtime or a missing or expired grant, and the token works again later. A token no row has,
 * such as an administrator's or one being checked during a sign-in, changes nothing.
 */
@Singleton
class RefusedSessions
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        fun refused(token: String): Job =
            scope.launch {
                try {
                    if (serverRepository.forgetToken(token)) Timber.i("A kid's session was refused by the server")
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w("Could not forget a refused session: %s", ex.javaClass.simpleName)
                }
            }
    }

/**
 * Passes the token of every request the server answers with 401 to [onRefused], see [RefusedSessions]. A network
 * interceptor, so it sees the authorization the SDK, [dev.aldoborrero.peke.kids.server.KidsApi] and the image loader
 * add.
 */
class RefusedTokenInterceptor(
    private val onRefused: (String) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401) tokenOf(request)?.let(onRefused)
        return response
    }

    companion object {
        private val TOKEN = Regex("""Token="([^"]+)"""")

        /**
         * The access token [request] goes out with: in Jellyfin's `Authorization` header, as the SDK and the app send
         * it, or in the older headers and query parameter
         */
        fun tokenOf(request: Request): String? =
            request.header("Authorization")?.let { TOKEN.find(it)?.groupValues?.get(1) }
                ?: request.header("X-Emby-Token")
                ?: request.header("X-MediaBrowser-Token")
                ?: request.url.queryParameter("api_key")
    }
}
