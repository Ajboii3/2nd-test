package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.Donation
import kotlinx.coroutines.flow.Flow

@Dao
interface DonationDao {
    @Query("SELECT * FROM donations ORDER BY timestamp DESC")
    fun getAllDonations(): Flow<List<Donation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonation(donation: Donation)

    @Query("DELETE FROM donations")
    suspend fun clearAllDonations()
}
