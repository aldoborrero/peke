package dev.aldoborrero.peke.util

/**
 * Hides what would let someone who reads the log sign in: a Quick Connect secret, an access token or an API key, in a
 * URL's query (`secret=…`, `api_key=…`), a JSON body (`"Secret":"…"`, `"AccessToken":"…"`) or an `Authorization`
 * header (`Token="…"`). Every line the app writes to logcat goes through it, and so does what the crash reports and
 * "Send app logs" read from there. The calls that carry these don't log them; this is for a library's log line or an
 * error message that quotes one.
 */
object LogRedaction {
    private const val HIDDEN = "******"

    private val patterns =
        listOf(
            Regex("""(?i)\b(secret|api_key|apikey|access_token|accesstoken|token)=([^&\s"',;)]+)"""),
            Regex("""(?i)("(?:secret|accesstoken|access_token|token|verifier|password|pw)"\s*:\s*")([^"]*)(")"""),
            Regex("""(?i)\b(Token=")([^"]*)(")"""),
        )

    fun redact(message: String): String {
        var result = message
        patterns.forEachIndexed { index, pattern ->
            result =
                pattern.replace(result) { match ->
                    val groups = match.groupValues
                    if (index == 0) "${groups[1]}=$HIDDEN" else "${groups[1]}$HIDDEN${groups[3]}"
                }
        }
        return result
    }
}
