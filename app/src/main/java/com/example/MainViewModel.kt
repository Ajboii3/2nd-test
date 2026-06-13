package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.AppDatabase
import com.example.db.DonationRepository
import com.example.model.Donation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"
    private val client = OkHttpClient()
    private val prefs = application.getSharedPreferences("NotifyAllPrefs", Context.MODE_PRIVATE)
    private val repository: DonationRepository

    // Webhook URL state
    var webhookUrl = mutableStateOf(prefs.getString("webhook_url", "") ?: "")
        private set

    // Service activity flag state
    var serviceEnabled = mutableStateOf(prefs.getBoolean("service_enabled", true))
        private set

    // Real-time History Feed from SQLite
    val donationsList: StateFlow<List<Donation>>

    init {
        val dao = AppDatabase.getDatabase(application).donationDao()
        repository = DonationRepository(dao)

        donationsList = repository.allDonations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun updateWebhookUrl(newValue: String) {
        webhookUrl.value = newValue
        prefs.edit().putString("webhook_url", newValue).apply()
        Log.d(TAG, "Saved Webhook URL: $newValue")
    }

    fun toggleServiceEnabled(newValue: Boolean) {
        serviceEnabled.value = newValue
        prefs.edit().putBoolean("service_enabled", newValue).apply()
        Log.d(TAG, "Saved Service Enabled: $newValue")
    }

    fun clearDonationsHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    // Sends a test webhook alert using the user's current URL for real overlay testing
    fun triggerMockTestAlert(senderName: String, amount: String, payApp: String, onComplete: (Boolean, String?) -> Unit) {
        val url = webhookUrl.value.trim()
        if (url.isEmpty()) {
            onComplete(false, "Configure Webhook URL first!")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("name", senderName)
                    put("username", senderName)
                    put("amount", amount)
                    put("currency", "INR")
                    put("app", payApp)
                    put("source", payApp)
                    put("message", "$senderName sent ₹$amount via $payApp (Test Alert)")
                    put("text", "$senderName sent ₹$amount via $payApp (Test Alert)")
                    put("provider", "custom")
                    put("isTest", true)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                val code = response.code
                response.close()

                val resultStatus = if (isSuccess) "TEST SUCCESS" else "TEST FAILED"
                val errorMsg = if (isSuccess) null else "Http status code: $code"

                // Save to history so they can see testing entries
                repository.insert(
                    Donation(
                        senderName = senderName,
                        amount = amount,
                        appName = payApp,
                        rawText = "MANUAL MOCK TEST: triggered manually by sender.",
                        webhookStatus = resultStatus,
                        errorMessage = errorMsg
                    )
                )

                launch(Dispatchers.Main) {
                    if (isSuccess) {
                        onComplete(true, null)
                    } else {
                        onComplete(false, "Alert posted, but stream service returned HTTP $code")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    onComplete(false, "Dispatch error: ${e.message}")
                }
            }
        }
    }
}
