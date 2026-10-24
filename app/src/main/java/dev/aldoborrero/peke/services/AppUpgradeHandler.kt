package dev.aldoborrero.peke.services

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.setup.KidSignOut
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private val serverDao: JellyfinServerDao,
        private val kidProfileDao: KidProfileDao,
        private val serverRepository: ServerRepository,
        private val kidSignOut: KidSignOut,
        private val keyValueService: KeyValueService,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
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
            /**
             * Where versions before spec 004 kept whether the TV was locked
             */
            private const val OLD_LOCK_KEY = "kids_lock"

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
        private suspend fun upgradeApp(
            previous: Version?,
            current: Version,
        ) {
            Timber.i("Upgrading from %s to %s", previous, current)
            // The most recent server and user, with the user's access token, which this TV no longer keeps (spec 004)
            context.getSharedPreferences("${context.packageName}_server", Context.MODE_PRIVATE).edit(commit = true) { clear() }
            keepOnlyKids()
        }

        /**
         * Keep only this TV's kids (spec 004, SC-002): sign out and delete every user row without a kid profile, such as
         * the administrator a TV set up by an older version kept, and drop what those versions kept that no longer
         * means anything: the `kids_lock` key, and the per-user PIN that was kept in plaintext. If the kids data can't
         * be read, every row counts as no kid: forgetting a kid only asks a grown-up to add them again (fail closed),
         * while keeping an administrator's token would not.
         *
         * The rows go right away; their sessions end on the server in the background, best effort.
         */
        suspend fun keepOnlyKids() {
            val kidIds =
                try {
                    kidProfileDao.allUserIds().toSet()
                } catch (ex: Exception) {
                    Timber.e(ex, "Could not read the kids on this TV")
                    emptySet()
                }
            val servers = withContext(PekeDispatchers.IO) { serverDao.getServers() }
            val noKids = servers.flatMap { s -> s.users.filter { it.rowId !in kidIds }.map { s.server to it } }
            noKids.forEach { (_, user) -> serverRepository.removeUser(user) }
            servers.flatMap { it.users }.filter { it.rowId in kidIds && (it.pin != null || it.requireLogin) }.forEach { kid ->
                withContext(PekeDispatchers.IO) { serverDao.addOrUpdateUser(kid.copy(pin = null, requireLogin = false)) }
            }
            keyValueService.remove(listOf(OLD_LOCK_KEY))
            if (noKids.isNotEmpty()) {
                Timber.i("Removed %d users who are no kids", noKids.size)
                scope.launch { noKids.forEach { (server, user) -> kidSignOut.signOut(server, user) } }
            }
        }
    }
