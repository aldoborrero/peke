package dev.aldoborrero.peke.kids.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.aldoborrero.peke.data.model.JellyfinUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

/**
 * Marks a device user as a kid profile and holds its kids mode settings, as last read from the Kids plugin.
 *
 * A row exists if and only if the server last said the user is a kid. It is a cache: changes go to the server.
 */
@Entity(
    tableName = "kid_profiles",
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
@TypeConverters(KidProfileConverters::class)
data class KidProfile(
    @PrimaryKey val userId: Int,
    val searchAllowed: Boolean = false,
    val autoplayEnabled: Boolean = false,
    val autoplayCap: Int = DEFAULT_AUTOPLAY_CAP,
    /**
     * Collections or playlists shown in the "Picked by your grown-ups" section, in order
     */
    val pickedItemIds: List<UUID> = emptyList(),
    val sections: List<KidsHomeSectionEntry> = KidsHomeSectionEntry.DEFAULT,
    /**
     * The server's revision of this profile, which changes are based on
     */
    val revision: Long = 0,
    /**
     * Minutes the kid may watch per day across devices, counted by the server; null means unlimited
     */
    val dailyAllowanceMinutes: Int? = null,
    /**
     * Minutes per sitting on a device, after which the time's up screen is shown; null means no limit
     */
    val sessionLimitMinutes: Int? = null,
    /**
     * The profile as the server last sent it, so changes keep fields this app does not know
     */
    val serverJson: String? = null,
) {
    val validAutoplayCap: Int get() = autoplayCap.coerceIn(MIN_AUTOPLAY_CAP, MAX_AUTOPLAY_CAP)

    companion object {
        const val DEFAULT_AUTOPLAY_CAP = 3
        const val MIN_AUTOPLAY_CAP = 1
        const val MAX_AUTOPLAY_CAP = 10
    }
}

enum class KidsHomeSection {
    KEEP_WATCHING,
    PICKED,
    LIBRARIES,
    NEW,
    FAVOURITES,
}

@Serializable
data class KidsHomeSectionEntry(
    val section: KidsHomeSection,
    val visible: Boolean = true,
) {
    companion object {
        val DEFAULT = KidsHomeSection.entries.map { KidsHomeSectionEntry(it) }

        /**
         * Drops duplicates and appends any missing section (visible) in default order
         */
        fun normalize(entries: List<KidsHomeSectionEntry>): List<KidsHomeSectionEntry> {
            val distinct = entries.distinctBy { it.section }
            val missing = KidsHomeSection.entries.filter { s -> distinct.none { it.section == s } }
            return distinct + missing.map { KidsHomeSectionEntry(it) }
        }
    }
}

class KidProfileConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromIds(ids: List<UUID>): String = json.encodeToString(ids.map { it.toString() })

    @TypeConverter
    fun toIds(value: String): List<UUID> =
        try {
            json.decodeFromString<List<String>>(value).mapNotNull { it.toUUIDOrNull() }
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing kid profile item ids")
            emptyList()
        }

    @TypeConverter
    fun fromSections(sections: List<KidsHomeSectionEntry>): String = json.encodeToString(sections)

    @TypeConverter
    fun toSections(value: String): List<KidsHomeSectionEntry> =
        try {
            KidsHomeSectionEntry.normalize(json.decodeFromString<List<KidsHomeSectionEntry>>(value))
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing kid profile sections")
            KidsHomeSectionEntry.DEFAULT
        }
}
