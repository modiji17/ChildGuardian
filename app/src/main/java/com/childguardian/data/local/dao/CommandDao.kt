package com.childguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.childguardian.data.local.entity.CommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: CommandEntity)

    @Update
    suspend fun updateCommand(command: CommandEntity)

    @Query("SELECT * FROM pending_commands WHERE status = 'pending'")
    fun getPendingCommands(): Flow<List<CommandEntity>>

    @Query("DELETE FROM pending_commands WHERE id = :id")
    suspend fun deleteCommand(id: Long)
}