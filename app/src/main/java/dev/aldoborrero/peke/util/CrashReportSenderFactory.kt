package dev.aldoborrero.peke.util

import android.content.Context
import com.google.auto.service.AutoService
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.kids.KidsDatabaseModule
import dev.aldoborrero.peke.services.hilt.AppModule
import dev.aldoborrero.peke.services.hilt.DatabaseModule
import dev.aldoborrero.peke.services.hilt.DeviceModule
import dev.aldoborrero.peke.services.hilt.forKid
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderException
import org.acra.sender.ReportSenderFactory
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.json.JSONObject
import timber.log.Timber
import java.util.Date

/**
 * Sends a crash report to the current server
 */
@AutoService(ReportSenderFactory::class)
class CrashReportSenderFactory : ReportSenderFactory {
    override fun create(
        context: Context,
        config: CoreConfiguration,
    ): ReportSender = CrashReportSender()

    override fun enabled(config: CoreConfiguration): Boolean = true
}

class CrashReportSender : ReportSender {
    override fun send(
        context: Context,
        errorContent: CrashReportData,
    ) {
        Timber.v("Attempting to send crash report")
        try {
            val api = lastKid(context)?.createApi(context)
            if (api != null) {
                val obj = JSONObject()
                for ((key, value) in errorContent.toMap()) {
                    obj.put(key, value)
                }
                val jsonStr = obj.toString(2)
                runBlocking {
                    val filename =
                        api.clientLogApi
                            .logFile(
                                """
                                ---
                                Date: ${Date()}
                                ---

                                """.trimIndent() + jsonStr,
                            ).content.fileName
                    Timber.i("Sent report to %s, filename=%s", api.baseUrl, filename)
                }
            } else {
                throw ReportSenderException("Could not find valid server and/or credentials to use")
            }
        } catch (ex: Exception) {
            throw ReportSenderException("Exception while sending crash report", ex)
        }
    }
}

/**
 * This TV's last kid with a session, or else its most recently signed-in one, read from the databases: a crash report
 * is sent from another process. Never a user without a kid profile.
 */
private fun lastKid(context: Context): CurrentUser? {
    val db = DatabaseModule.database(context)
    val kidsDb = KidsDatabaseModule.kidsDatabase(context)
    return try {
        val (kidIds, lastKidRowId) =
            runBlocking { kidsDb.kidProfileDao().allUserIds().toSet() to kidsDb.tvSetupDao().get()?.lastKidRowId }
        val kids =
            db
                .serverDao()
                .getServers()
                .flatMap { s -> s.users.filter { it.rowId in kidIds && it.accessToken != null }.map { CurrentUser(s.server, it) } }
        kids.firstOrNull { it.user.rowId == lastKidRowId }
            ?: kids.maxByOrNull { it.user.lastUsed?.toEpochSecond() ?: Long.MIN_VALUE }
    } finally {
        kidsDb.close()
        db.close()
    }
}

private fun CurrentUser.createApi(context: Context): ApiClient =
    run {
        val okHttpClient =
            OkHttpClient
                .Builder()
                .build()
        val okHttpFactory = OkHttpFactory(okHttpClient)
        val api =
            createJellyfin {
                this.context = context
                clientInfo = AppModule.clientInfo(context)
                // The id the saved user's other calls go out with
                deviceInfo = DeviceModule.deviceInfo(context).forKid(user.id)
                apiClientFactory = okHttpFactory
                socketConnectionFactory = okHttpFactory
                minimumServerVersion = Jellyfin.minimumVersion
            }.createApi(baseUrl = server.url, accessToken = user.accessToken)
        api
    }
