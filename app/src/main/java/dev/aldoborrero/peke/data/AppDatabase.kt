package dev.aldoborrero.peke.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.aldoborrero.peke.data.model.GetItemsFilter
import dev.aldoborrero.peke.data.model.ItemPlayback
import dev.aldoborrero.peke.data.model.ItemTrackModification
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.LibraryDisplayInfo
import dev.aldoborrero.peke.data.model.PlaybackEffect
import dev.aldoborrero.peke.data.model.RememberedTab
import dev.aldoborrero.peke.data.model.SeriesTrackChoice
import dev.aldoborrero.peke.ui.components.ViewOptions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Database(
    entities = [
        JellyfinServer::class,
        JellyfinUser::class,
        ItemPlayback::class,
        LibraryDisplayInfo::class,
        PlaybackEffect::class,
        ItemTrackModification::class,
        RememberedTab::class,
        SeriesTrackChoice::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): JellyfinServerDao

    abstract fun itemPlaybackDao(): ItemPlaybackDao

    abstract fun libraryDisplayInfoDao(): LibraryDisplayInfoDao

    abstract fun playbackEffectDao(): PlaybackEffectDao

    abstract fun rememberedTabDao(): RememberedTabDao

    abstract fun seriesTrackChoiceDao(): SeriesTrackChoiceDao
}

class Converters {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @TypeConverter
    fun convertToString(id: UUID): String = id.toString().replace("-", "")

    @TypeConverter
    fun convertToUUID(str: String): UUID = str.toUUID()

    @TypeConverter
    fun convertItemSortBy(sort: ItemSortBy): String = sort.serialName

    @TypeConverter
    fun convertItemSortBy(sort: String): ItemSortBy? = ItemSortBy.fromNameOrNull(sort)

    @TypeConverter
    fun convertSortOrder(sort: SortOrder): String = sort.serialName

    @TypeConverter
    fun convertSortOrder(sort: String): SortOrder? = SortOrder.fromNameOrNull(sort)

    @TypeConverter
    fun convertGetItemsFilter(filter: GetItemsFilter): String = json.encodeToString(filter)

    @TypeConverter
    fun convertGetItemsFilter(filter: String): GetItemsFilter =
        try {
            json.decodeFromString(filter)
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing filter")
            GetItemsFilter()
        }

    @TypeConverter
    fun convertViewOptions(viewOptions: ViewOptions?): String? = viewOptions?.let { json.encodeToString(viewOptions) }

    @TypeConverter
    fun convertViewOptions(viewOptions: String?): ViewOptions? =
        try {
            viewOptions?.let { json.decodeFromString(viewOptions) }
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing view options")
            null
        }

    @TypeConverter
    fun convertToString(dateTime: ZonedDateTime): String = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(dateTime)

    @TypeConverter
    fun convertToLocalDateTime(dateTime: String): ZonedDateTime = ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_ZONED_DATE_TIME)
}

class ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(ZonedDateTime::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZonedDateTime =
        ZonedDateTime.parse(decoder.decodeString(), DateTimeFormatter.ISO_ZONED_DATE_TIME)

    override fun serialize(
        encoder: Encoder,
        value: ZonedDateTime,
    ) {
        encoder.encodeString(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(value))
    }
}
