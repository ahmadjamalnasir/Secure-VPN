package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY is_premium ASC, country ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Query("DELETE FROM servers")
    suspend fun clearAll()
}
