package com.reuniware.celltowerradar.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reuniware.celltowerradar.model.CellTowerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CellTowerDao {
    @Query("SELECT * FROM cell_towers ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CellTowerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tower: CellTowerEntity)

    @Query("SELECT * FROM cell_towers WHERE id = :id LIMIT 1")
    suspend fun getTowerById(id: String): CellTowerEntity?

    @Query("DELETE FROM cell_towers")
    suspend fun clearHistory()
}
