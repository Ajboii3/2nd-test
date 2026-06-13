package com.example

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.DonationRepository
import com.example.model.Donation
import com.example.parser.AlertDeduplicator
import com.example.parser.UpiNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MyNotificationListenerService : NotificationListenerService() {

    private val TAG = "NotificationListener"
    private val client = OkHttpClient()
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: DonationRepository

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(applicationContext).donationDao()
        repository = DonationRepository(dao)
        Log.d(TAG, "Notification Listener Service Created!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefs = getSharedPreferences("NotifyAllPrefs", Context.MODE_PRIVATE)
        val isServiceEnabled = prefs.getBoolean("service_enabled", true)
        if (!isServiceEnabled) {
            Log.d(TAG, "Service is globally disabled in settings. Skipping notification.")
            return
        }

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Process notification using regex parser
        val parsed = UpiNotificationParser.parse(title, text, packageName)

        if (parsed.isPayment) {
            handleParsedPayment(title, text, parsed, prefs)
        }
    }

    private fun handleParsedPayment(
        title: String,
        text: String,
        parsed: com.example.parser.ParsedDonation,
        prefs: android.content.SharedPreferences
    ) {
        val webhookUrl = prefs.getString("webhook_url", "") ?: ""
        val rawMessage = "Title: $title | Text: $text"

        // Check deduplication sliding window
        val isDup = AlertDeduplicator.isDuplicate(parsed.appName, parsed.senderName, parsed.amount)
        if (isDup) {
            Log.w(TAG, "Deduplicated repeated alert: ${parsed.senderName} - ${parsed.amount}")
            serviceScope.launch {
                repository.insert(
                    Donation(
                        senderName = parsed.senderName,
                        amount = parsed.amount,
                        appName = parsed.appName,
                        rawText = rawMessage,
                        webhookStatus = "DEDUPLICATED",
                        errorMessage = "Prevented double audio playing (6s time window)"
                    )
                )
            }
            return
        }

        // Trigger Webhook asynchronously if not duplicated
        if (webhookUrl.isEmpty()) {
            serviceScope.launch {
                repository.insert(
                    Donation(
                        senderName = parsed.senderName,
                        amount = parsed.amount,
                        appName = parsed.appName,
                        rawText = rawMessage,
                        webhookStatus = "FAILED",
                        errorMessage = "Webhook URL is not configured in settings."
                    )
                )
            }
            return
        }

        serviceScope.launch {
            try {
                // Post payload containing rich keys to support customized overlay widgets seamlessly
                val json = JSONObject().apply {
                    put("name", parsed.senderName)
                    put("username", parsed.senderName)
                    put("amount", parsed.amount)
                    put("currency", "INR")
                    put("app", parsed.appName)
                    put("source", parsed.appName)
                    put("message", "${parsed.senderName} sent ₹${parsed.amount} via ${parsed.appName}")
                    put("text", "${parsed.senderName} sent ₹${parsed.amount} via ${parsed.appName}")
                    put("provider", "custom")
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                val code = response.code
                response.close()

                if (isSuccess) {
                    repository.insert(
                        Donation(
                            senderName = parsed.senderName,
                            amount = parsed.amount,
                            appName = parsed.appName,
                            rawText = rawMessage,
                            webhookStatus = "SUCCESS"
                        )
                    )
                    Log.d(TAG, "Successfully triggered webhook alert!")
                } else {
                    repository.insert(
                        Donation(
                            senderName = parsed.senderName,
                            amount = parsed.amount,
                            appName = parsed.appName,
                            rawText = rawMessage,
                            webhookStatus = "FAILED",
                            errorMessage = "HTTP code: $code"
                        )
                    )
                    Log.e(TAG, "Webhook call failed with response code: $code")
                }
            } catch (e: Exception) {
                repository.insert(
                    Donation(
                        senderName = parsed.senderName,
                        amount = parsed.amount,
                        appName = parsed.appName,
                        rawText = rawMessage,
                        webhookStatus = "FAILED",
                        errorMessage = e.message ?: "Network timeout/error"
                    )
                )
                Log.e(TAG, "Exception during webhook dispatch: ${e.message}", e)
            }
        }
    }
}
