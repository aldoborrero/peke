package dev.aldoborrero.peke.kids.server

import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsHomeSection
import dev.aldoborrero.peke.kids.data.KidsHomeSectionEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

/**
 * JSON shapes of the Jellyfin Kids plugin API, version 1. The plugin answers in PascalCase and omits nulls.
 */
val KidsJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

const val KIDS_API_VERSION = 1

object KidsFeatures {
    const val PROFILES = "profiles"
    const val PARENT_LOCK = "parentLock"
    const val ALLOWANCE = "allowance"

    /**
     * The per-kid sitting limit (`SessionLimitMinutes`) in the profile
     */
    const val SESSION_LIMIT = "sessionLimit"

    /**
     * The server can stop and refuse a denied kid's playback (`Enforced`, `DeniedReason` in the status)
     */
    const val ENFORCEMENT = "enforcement"

    /**
     * The server counts the sitting across the kid's devices, with a household break, and reports it in the status
     */
    const val SITTING = "sitting"

    /**
     * Live TV for kids (plugin 0.0.6.0): `LiveTvChannelIds` in the profile, the kid's channels and guide, and live TV
     * refusals. Optional: without it a kid has no live TV.
     */
    const val LIVE_TV = "liveTv"

    /**
     * Undo a bonus just given (plugin 0.0.9.0): `BonusId` in the bonus answer, `DELETE …/WatchTime/Bonus/{id}`
     */
    const val BONUS_UNDO = "bonusUndo"

    /**
     * End a kid's break after the sitting (plugin 0.0.9.0): `POST …/WatchTime/Break/End`
     */
    const val BREAK_END = "breakEnd"

    /**
     * Kid avatars (plugin 0.0.8.0, Kids API 1.1): `Avatar` in the kid profile, one of the six pictures Peke ships
     * ([dev.aldoborrero.peke.ui.theme.PekeAvatar]), omitted when the kid has none
     */
    const val AVATAR = "avatar"

    /**
     * Kids mode needs all of these (plugin 0.0.5.0 or later), since only the server keeps time
     */
    val REQUIRED = listOf(PROFILES, PARENT_LOCK, ENFORCEMENT, SITTING)
}

@Serializable
data class KidsInfo(
    @SerialName("PluginVersion") val pluginVersion: String? = null,
    @SerialName("ApiVersion") val apiVersion: Int? = null,
    @SerialName("Features") val features: List<String> = emptyList(),
    @SerialName("LiveTv") val liveTv: KidsLiveTvGate? = null,
)

/**
 * Whether the plugin guards every live TV route: `ok`, `degraded` (with the routes in [missing]) or `pending`
 */
@Serializable
data class KidsLiveTvGate(
    @SerialName("Gate") val gate: String? = null,
    @SerialName("Missing") val missing: List<String> = emptyList(),
)

/**
 * Jellyfin's `QueryResult<BaseItemDto>`, as the Kids live TV routes answer it, with only the fields kids mode reads
 */
@Serializable
data class KidsItemsResult(
    @SerialName("Items") val items: List<KidsLiveTvItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

/**
 * A live TV channel or program (`BaseItemDto`) from the Kids live TV routes
 */
@Serializable
data class KidsLiveTvItem(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("ChannelId") val channelId: String? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("CurrentProgram") val currentProgram: KidsLiveTvItem? = null,
)

/**
 * The oldest plugin version Peke sets a TV up with (spec FR-002)
 */
const val MIN_PLUGIN_VERSION = "0.0.7.0"

/**
 * Whether [version], such as `0.0.7.0`, is [MIN_PLUGIN_VERSION] or later. A missing or unreadable version is not.
 */
fun pluginVersionSupported(version: String?): Boolean {
    val parts = version?.trim()?.split('.')?.map { it.toIntOrNull() ?: return false } ?: return false
    val min = MIN_PLUGIN_VERSION.split('.').map { it.toInt() }
    for (i in 0 until maxOf(parts.size, min.size)) {
        val a = parts.getOrElse(i) { 0 }
        val b = min.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return true
}

/**
 * Whether a server can be set up: it has the Kids plugin in a version Peke sets TVs up with
 */
enum class PluginSupport {
    SUPPORTED,

    /**
     * No plugin, or one older than [MIN_PLUGIN_VERSION]
     */
    NEEDED,
    UNREACHABLE,
}

/**
 * A Jellyfin user as `GET /Users` lists them, with only what setup shows
 */
@Serializable
data class HouseholdUserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

/**
 * Jellyfin's state of a Quick Connect request (`GET /QuickConnect/Connect`), with only whether it was approved; the
 * secret it also carries is never read from it
 */
@Serializable
data class QuickConnectState(
    @SerialName("Authenticated") val authenticated: Boolean = false,
)

/**
 * A kid of the household: a Jellyfin user with a profile in the Kids plugin
 */
data class HouseholdKid(
    val userId: UUID,
    val name: String?,
    val imageTag: String?,
)

@Serializable
data class KidProfileRef(
    @SerialName("UserId") val userId: String,
    @SerialName("Revision") val revision: Long,
)

@Serializable
data class LockStatus(
    @SerialName("LockType") val lockType: String? = null,
    @SerialName("ChangedAt") val changedAt: String? = null,
)

@Serializable
data class VerifyResponse(
    @SerialName("Valid") val valid: Boolean = false,
    @SerialName("Grant") val grant: String? = null,
    @SerialName("ExpiresAt") val expiresAt: String? = null,
    @SerialName("RetryAfterSeconds") val retryAfterSeconds: Int? = null,
)

@Serializable
data class VerifierResponse(
    @SerialName("LockType") val lockType: String,
    @SerialName("Verifier") val verifier: String,
)

@Serializable
data class SetLockRequest(
    @SerialName("LockType") val lockType: String,
    @SerialName("Code") val code: String,
    @SerialName("Current") val current: String? = null,
)

@Serializable
data class KidsStatus(
    @SerialName("Date") val date: String = "",
    @SerialName("UsedSeconds") val usedSeconds: Long = 0,
    @SerialName("AllowanceSeconds") val allowanceSeconds: Long? = null,
    @SerialName("BonusSeconds") val bonusSeconds: Long = 0,
    @SerialName("RemainingSeconds") val remainingSeconds: Long? = null,
    @SerialName("TimeUp") val timeUp: Boolean = false,
    @SerialName("ServerTime") val serverTime: String? = null,
    @SerialName("NextDayStartsAt") val nextDayStartsAt: String? = null,
    @SerialName("WarnAtSeconds") val warnAtSeconds: List<Long> = listOf(300, 60),
    @SerialName("Enforced") val enforced: Boolean = false,
    @SerialName("DeniedReason") val deniedReason: String? = null,
    @SerialName("DeniedUntil") val deniedUntil: String? = null,
    @SerialName("SittingLimitSeconds") val sittingLimitSeconds: Long? = null,
    @SerialName("SittingUsedSeconds") val sittingUsedSeconds: Long? = null,
    @SerialName("SittingRemainingSeconds") val sittingRemainingSeconds: Long? = null,
    @SerialName("BreakRemainingSeconds") val breakRemainingSeconds: Long? = null,
    @SerialName("BreakSeconds") val breakSeconds: Long? = null,
) {
    val unlimited: Boolean get() = allowanceSeconds == null

    /**
     * Why the kid may not watch now, by the server's figures, or null if they may
     *
     * With enforcement on, the server says why. Without it, time's up and the break after the sitting limit still
     * apply, from the allowance and sitting fields.
     */
    val denial: KidsDenial?
        get() =
            when {
                // The server reports the break over, so the sitting no longer denies
                deniedReason == KidsDenial.SITTING_OVER.reason && breakRemainingSeconds == 0L -> null

                deniedReason != null -> KidsDenial.of(deniedReason)

                timeUp -> KidsDenial.TIME_UP

                (breakRemainingSeconds ?: 0) > 0 -> KidsDenial.SITTING_OVER

                else -> null
            }
}

/**
 * The answer to a bonus: the kid's new status and, with [KidsFeatures.BONUS_UNDO], the id that takes it back
 *
 * @param undoSeconds how long the server still accepts the undo (`BonusUndoUntil` less `ServerTime`, both on the
 * server's clock), or null if it didn't say
 */
data class KidsBonus(
    val status: KidsStatus,
    val bonusId: Long?,
    val undoSeconds: Long? = null,
) {
    companion object {
        fun of(
            status: KidsStatus,
            undo: BonusUndoDto,
        ): KidsBonus {
            val until = undo.undoUntil?.let(::instantOrNull)
            val now = status.serverTime?.let(::instantOrNull)
            val seconds =
                if (until != null && now != null) {
                    java.time.Duration
                        .between(now, until)
                        .seconds
                } else {
                    null
                }
            return KidsBonus(status, undo.bonusId, seconds)
        }

        private fun instantOrNull(value: String): java.time.Instant? =
            try {
                java.time.OffsetDateTime
                    .parse(value)
                    .toInstant()
            } catch (_: java.time.format.DateTimeParseException) {
                null
            }
    }
}

/**
 * The bonus fields of the bonus answer (plugin 0.0.9.0)
 */
@Serializable
data class BonusUndoDto(
    @SerialName("BonusId") val bonusId: Long? = null,
    @SerialName("BonusUndoUntil") val undoUntil: String? = null,
)

/**
 * Why the server does not let a kid watch now, from `DeniedReason` or the `X-Kids-Reason` header
 */
enum class KidsDenial(
    val reason: String,
) {
    TIME_UP("timeUp"),
    BEDTIME("outsideSchedule"),
    SITTING_OVER("sessionLimit"),
    ;

    companion object {
        /**
         * The denial for a server [reason], or null without one. A reason this app doesn't know still denies (fail
         * closed), as time's up.
         */
        fun of(reason: String?): KidsDenial? {
            if (reason.isNullOrBlank()) return null
            return entries.firstOrNull { it.reason.equals(reason.trim(), ignoreCase = true) } ?: TIME_UP
        }
    }
}

/**
 * Converts between the plugin's `KidProfile` object and the cached [KidProfile]
 */
object KidProfileMapper {
    private val sectionNames =
        mapOf(
            KidsHomeSection.PICKED to "Picked",
            KidsHomeSection.KEEP_WATCHING to "KeepWatching",
            KidsHomeSection.LIBRARIES to "Libraries",
            KidsHomeSection.NEW to "New",
            KidsHomeSection.FAVOURITES to "Favourites",
        )

    const val REVISION = "Revision"
    private const val USER_ID = "UserId"
    private const val SEARCH_ALLOWED = "SearchAllowed"
    private const val AUTOPLAY_ENABLED = "AutoplayEnabled"
    private const val AUTOPLAY_CAP = "AutoplayCap"
    private const val SECTIONS = "Sections"
    private const val SECTION = "Section"
    private const val VISIBLE = "Visible"
    private const val PICKED_ITEM_IDS = "PickedItemIds"
    const val DAILY_ALLOWANCE_MINUTES = "DailyAllowanceMinutes"
    const val SESSION_LIMIT_MINUTES = "SessionLimitMinutes"
    const val LIVE_TV_CHANNEL_IDS = "LiveTvChannelIds"
    private const val AVATAR = "Avatar"

    fun sectionName(section: KidsHomeSection): String = sectionNames.getValue(section)

    fun sectionOf(name: String?): KidsHomeSection? = sectionNames.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key

    /**
     * The cached profile for device user [userRowId] of server [serverId] from a server profile object
     */
    fun fromServer(
        userRowId: Int,
        serverId: UUID?,
        obj: JsonObject,
    ): KidProfile {
        val sections =
            (obj[SECTIONS] as? JsonArray)
                ?.mapNotNull { entry ->
                    val o = entry as? JsonObject ?: return@mapNotNull null
                    val section = sectionOf(o[SECTION].asString()) ?: return@mapNotNull null
                    KidsHomeSectionEntry(section, o[VISIBLE].asBoolean() ?: true)
                }.orEmpty()
        val picks =
            (obj[PICKED_ITEM_IDS] as? JsonArray)
                ?.mapNotNull { it.asString()?.toUUIDOrNull() }
                .orEmpty()
        return KidProfile(
            userId = userRowId,
            serverId = serverId,
            searchAllowed = obj[SEARCH_ALLOWED].asBoolean() ?: false,
            autoplayEnabled = obj[AUTOPLAY_ENABLED].asBoolean() ?: false,
            autoplayCap = obj[AUTOPLAY_CAP].asInt() ?: KidProfile.DEFAULT_AUTOPLAY_CAP,
            pickedItemIds = picks,
            sections = KidsHomeSectionEntry.normalize(sections),
            revision = obj[REVISION].asLong() ?: 0,
            dailyAllowanceMinutes = obj[DAILY_ALLOWANCE_MINUTES].asInt(),
            sessionLimitMinutes = obj[SESSION_LIMIT_MINUTES].asInt(),
            serverJson = obj.toString(),
        )
    }

    /**
     * Whether the server sent a sitting limit for this profile
     */
    fun hasSessionLimitField(profile: KidProfile): Boolean = profile.serverObject()?.containsKey(SESSION_LIMIT_MINUTES) == true

    /**
     * The live TV channels a grown-up picked for the kid, in order, as the server last sent them. Only the dashboard
     * edits them, so they have no column of their own: they live in [KidProfile.serverJson].
     */
    fun liveTvChannelIds(profile: KidProfile): List<UUID> =
        (profile.serverObject()?.get(LIVE_TV_CHANNEL_IDS) as? JsonArray)
            ?.mapNotNull { it.asString()?.toUUIDOrNull() }
            .orEmpty()

    /**
     * The kid's avatar id as the server last sent it, or null when it sent none. Only the dashboard picks it, so it has
     * no column of its own and is sent back unchanged with every save ([toUpdate]).
     */
    fun avatarId(profile: KidProfile): String? = profile.serverObject()?.get(AVATAR).asString()

    /**
     * The body of a `PUT` for [profile]: the last server object, with every field the app edits replaced, so fields
     * the app does not know are kept
     *
     * @param revision the revision the change is based on
     */
    fun toUpdate(
        profile: KidProfile,
        revision: Long,
        sendSessionLimit: Boolean,
    ): JsonObject {
        val base = profile.serverObject().orEmpty()
        return buildJsonObject {
            base.forEach { (key, value) ->
                if (key != USER_ID) put(key, value)
            }
            put(REVISION, JsonPrimitive(revision))
            put(SEARCH_ALLOWED, JsonPrimitive(profile.searchAllowed))
            put(AUTOPLAY_ENABLED, JsonPrimitive(profile.autoplayEnabled))
            put(AUTOPLAY_CAP, JsonPrimitive(profile.validAutoplayCap))
            put(
                SECTIONS,
                JsonArray(
                    profile.sections.map {
                        buildJsonObject {
                            put(SECTION, JsonPrimitive(sectionName(it.section)))
                            put(VISIBLE, JsonPrimitive(it.visible))
                        }
                    },
                ),
            )
            put(PICKED_ITEM_IDS, JsonArray(profile.pickedItemIds.map { JsonPrimitive(it.toString()) }))
            // The PUT is a full replacement, so leaving these out would clear them
            put(DAILY_ALLOWANCE_MINUTES, profile.dailyAllowanceMinutes?.let(::JsonPrimitive) ?: JsonNull)
            if (sendSessionLimit || base.containsKey(SESSION_LIMIT_MINUTES)) {
                put(SESSION_LIMIT_MINUTES, profile.sessionLimitMinutes?.let(::JsonPrimitive) ?: JsonNull)
            }
            // LIVE_TV_CHANNEL_IDS is copied from base as the server sent it: omitted or empty would mean no live TV,
            // and only the dashboard edits it. A plugin without live TV never sent it, and gets none.
        }
    }

    private fun KidProfile.serverObject(): JsonObject? =
        serverJson?.let {
            try {
                KidsJson.parseToJsonElement(it).jsonObject
            } catch (_: Exception) {
                null
            }
        }

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull
}
