package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForensicCaseDao {
    @Query("SELECT * FROM forensic_cases ORDER BY createdAtMs DESC")
    fun observeAllCases(): Flow<List<ForensicCaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(entity: ForensicCaseEntity)

    @Query("DELETE FROM forensic_cases WHERE id = :id")
    suspend fun deleteCaseById(id: String)

    @Query("DELETE FROM forensic_cases")
    suspend fun clearAll()
}
