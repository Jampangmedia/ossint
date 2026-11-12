package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ForensicCaseEntity::class], version = 2, exportSchema = false)
abstract class SignalTraceDatabase : RoomDatabase() {
    abstract fun forensicCaseDao(): ForensicCaseDao

    companion object {
        @Volatile
        private var INSTANCE: SignalTraceDatabase? = null

        fun getInstance(context: Context): SignalTraceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignalTraceDatabase::class.java,
                    "signaltrace_forensics.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
