package com.reuniware.celltowerradar.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reuniware.celltowerradar.model.CellTowerEntity

@Database(entities = [CellTowerEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cellTowerDao(): CellTowerDao
}
