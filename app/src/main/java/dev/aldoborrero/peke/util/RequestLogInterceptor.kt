package dev.aldoborrero.peke.util

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Debug builds only: logs one line per request, so the calls a session makes can be counted (spec 010). Images go to
 * [IMAGE_TAG] with their size parameters, everything else to [TAG]. A line has the method, the path with ids shortened,
 * the query parameter names (values only for [SHOWN_VALUES]), the status, the time to the end of the body, the body's
 * bytes and the page on top when the request went out. Never headers, tokens, `api_key` or bodies.
 *
 * The line is written when the body is closed, so the time and the bytes are the whole answer's.
 */
class RequestLogInterceptor(
    private val screen: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val page = runCatching { screen() }.getOrNull()
        val start = System.nanoTime()
        val response =
            try {
                chain.proceed(request)
            } catch (ex: Exception) {
                log(request.url, describe(request.method, request.url, ex.javaClass.simpleName, start, null, page))
                throw ex
            }
        val body = response.body
        return response
            .newBuilder()
            .body(
                CountingBody(body) { bytes ->
                    log(request.url, describe(request.method, request.url, response.code.toString(), start, bytes, page))
                },
            ).build()
    }

    private fun log(
        url: HttpUrl,
        line: String,
    ) {
        Timber.tag(if (isImage(url)) IMAGE_TAG else TAG).d(line)
    }

    private class CountingBody(
        private val delegate: ResponseBody,
        private val onDone: (Long) -> Unit,
    ) : ResponseBody() {
        private var bytes = 0L
        private var done = false

        private fun finish() {
            if (!done) {
                done = true
                onDone(bytes)
            }
        }

        private val counted: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read == -1L) finish() else bytes += read
                    return read
                }

                override fun close() {
                    finish()
                    super.close()
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun source(): BufferedSource = counted

        override fun close() {
            finish()
            delegate.close()
        }
    }

    companion object {
        const val TAG = "PekeNet"
        const val IMAGE_TAG = "PekeImg"

        private val SHOWN_VALUES =
            setOf(
                "fields",
                "limit",
                "startindex",
                "enabletotalrecordcount",
                "imagetypelimit",
                "maxheight",
                "maxwidth",
                "fillheight",
                "fillwidth",
                "quality",
            )
        private val HIDDEN_NAMES = setOf("api_key", "apikey", "access_token", "accesstoken", "token", "secret")
        private val ID = Regex("""^[0-9a-fA-F]{32}$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")

        internal fun isImage(url: HttpUrl): Boolean = url.pathSegments.any { it.equals("Images", ignoreCase = true) }

        /** An id becomes its first 6 characters, enough to see the same item asked twice */
        internal fun shortPath(url: HttpUrl): String =
            url.pathSegments.joinToString("/", prefix = "/") { segment ->
                if (ID.matches(segment)) "{" + segment.replace("-", "").take(6) + "}" else segment
            }

        internal fun shortQuery(url: HttpUrl): String =
            url.queryParameterNames
                .filterNot { it.lowercase() in HIDDEN_NAMES }
                .sorted()
                .joinToString(",") { name ->
                    if (name.lowercase() in SHOWN_VALUES) {
                        "$name=" + url.queryParameterValues(name).joinToString("|")
                    } else {
                        name
                    }
                }

        internal fun describe(
            method: String,
            url: HttpUrl,
            status: String,
            start: Long,
            bytes: Long?,
            page: String?,
        ): String {
            val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            val query = shortQuery(url)
            return buildString {
                append(method).append(' ').append(shortPath(url))
                if (query.isNotEmpty()) append(" ?").append(query)
                append(" -> ").append(status)
                append(' ').append(ms).append("ms")
                if (bytes != null) append(' ').append(bytes).append('B')
                if (page != null) append(" @").append(page)
            }
        }
    }
}
