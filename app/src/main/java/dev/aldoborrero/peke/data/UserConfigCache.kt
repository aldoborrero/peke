package dev.aldoborrero.peke.data

import dev.aldoborrero.peke.services.KeyValueService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last user configuration the server sent for each user, so a kid opened from their stored row, without asking
 * the server first, starts with their own settings (audio and subtitle languages, live TV access) rather than the
 * defaults. Holds no token.
 */
@Singleton
class UserConfigCache
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
    ) {
        suspend fun get(userId: UUID): UserDto? =
            try {
                keyValueService.get<UserDto>(key(userId)).first()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not read the cached user configuration: %s", ex.javaClass.simpleName)
                null
            }

        suspend fun save(userDto: UserDto) {
            try {
                keyValueService.save(key(userDto.id), userDto)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not cache the user configuration: %s", ex.javaClass.simpleName)
            }
        }

        suspend fun forget(userId: UUID) {
            try {
                keyValueService.remove(listOf(key(userId)))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not forget the cached user configuration: %s", ex.javaClass.simpleName)
            }
        }

        private fun key(userId: UUID) = "${userId}_user_config"
    }
