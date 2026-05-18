package com.reuniware.celltowerradar.di

import android.content.Context
import androidx.room.Room
import com.reuniware.celltowerradar.repository.AppDatabase
import com.reuniware.celltowerradar.repository.CellTowerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cell-tower-db"
        ).build()
    }

    @Provides
    fun provideCellTowerDao(database: AppDatabase): CellTowerDao {
        return database.cellTowerDao()
    }
}
