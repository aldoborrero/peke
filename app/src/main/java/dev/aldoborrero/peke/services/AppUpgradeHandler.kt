package dev.aldoborrero.peke.services

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.util.Version
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles any changes needed when the app in upgraded on the device such as setting new preferences
 */
@Singleton
class AppUpgradeHandler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val pkgInfo: PackageInfo get() = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion: Version get() = Version.fromString(pkgInfo.versionName!!)

        fun needUpgrade(): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val previousVersion = prefs.getString(VERSION_NAME_CURRENT_KEY, null)
            val previousVersionCode = prefs.getLong(VERSION_CODE_CURRENT_KEY, -1)

            val newVersion = pkgInfo.versionName!!
            val newVersionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    pkgInfo.versionCode.toLong()
                }
            Timber.i(
                "App versions: $previousVersion=>$newVersion, $previousVersionCode=>$newVersionCode",
            )
            return newVersion != previousVersion || newVersionCode != previousVersionCode
        }

        suspend fun run() {
            Timber.i("App upgrade started")
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val previousVersion = prefs.getString(VERSION_NAME_CURRENT_KEY, null)
            val previousVersionCode = prefs.getLong(VERSION_CODE_CURRENT_KEY, -1)

            val newVersion = pkgInfo.versionName!!
            val newVersionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    pkgInfo.versionCode.toLong()
                }
            // Store the previous and new version info
            prefs.edit(true) {
                putString(VERSION_NAME_PREVIOUS_KEY, previousVersion)
                putLong(VERSION_CODE_PREVIOUS_KEY, previousVersionCode)
                putString(VERSION_NAME_CURRENT_KEY, newVersion)
                putLong(VERSION_CODE_CURRENT_KEY, newVersionCode)
            }
            try {
                copySubfont(true)
                upgradeApp(previousVersion?.let(Version::tryFromString), Version.fromString(newVersion))
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during app upgrade")
            }
            Timber.i("App upgrade complete")
        }

        /**
         * Copies the font file used by MPV subtitles to the app's files directory
         */
        fun copySubfont(overwrite: Boolean) {
            try {
                val fontFileName = "subfont.ttf"
                val outputFile = File(context.filesDir, fontFileName)
                if (!outputFile.exists() || overwrite) {
                    context.assets.open(fontFileName).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Timber.i("Wrote font %s to local", fontFileName)
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception copying subfont.tff")
            }
        }

        companion object {
            const val VERSION_NAME_PREVIOUS_KEY = "version.previous.name"
            const val VERSION_CODE_PREVIOUS_KEY = "version.previous.code"
            const val VERSION_NAME_CURRENT_KEY = "version.current.name"
            const val VERSION_CODE_CURRENT_KEY = "version.current.code"
        }

        /**
         * Perform any needed upgrades, keyed to Peke's versions, e.g.
         * `if (previous != null && previous.isLessThan(Version.fromString("0.2.0"))) { … }`
         *
         * @param previous the version upgraded from, null on a fresh install, which gets its defaults from
         * `AppPreferencesSerializer` and needs no step
         */
        private fun upgradeApp(
            previous: Version?,
            current: Version,
        ) {
            Timber.i("Upgrading from %s to %s", previous, current)
            // The most recent server and user, with the user's access token, which this TV no longer keeps (spec 004)
            context.getSharedPreferences("${context.packageName}_server", Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }
    }
