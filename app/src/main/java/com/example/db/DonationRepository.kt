package com.example.db

import com.example.model.Donation
import kotlinx.coroutines.flow.Flow

class DonationRepository(private val donationDao: DonationDao) {
    val allDonations: Flow<List<Donation>> = donationDao.getAllDonations()

    suspend fun insert(donation: Donation) {
        donationDao.insertDonation(donation)
    }

    suspend fun clearAll() {
        donationDao.clearAllDonations()
    }
}
