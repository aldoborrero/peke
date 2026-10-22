package dev.aldoborrero.peke.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.LibraryDisplayInfo
import dev.aldoborrero.peke.ui.toServerString
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface LibraryDisplayInfoDao {
    fun getItem(
        user: JellyfinUser,
        itemId: UUID,
    ): LibraryDisplayInfo? = getItem(user.rowId, itemId.toServerString())

    fun getItem(
        user: JellyfinUser,
        itemId: String,
    ): LibraryDisplayInfo? = getItem(user.rowId, itemId)

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId AND itemId=:itemId")
    fun getItem(
        userId: Int,
        itemId: String,
    ): LibraryDisplayInfo?

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId AND itemId=:itemId")
    fun getItemAsFlow(
        userId: Int,
        itemId: String,
    ): Flow<LibraryDisplayInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveItem(item: LibraryDisplayInfo): Long

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId")
    fun getItems(userId: Int): List<LibraryDisplayInfo>
}
