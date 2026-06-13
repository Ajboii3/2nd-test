package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Donation
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NotifyAllDashboard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Check if Notification Listener permission is granted
fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val myComponent = ComponentName(context, MyNotificationListenerService::class.java)
    return flat?.contains(myComponent.flattenToString()) == true
}

@Composable
fun NotifyAllDashboard(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // Recheck permission dynamically whenever the user focuses back on the app
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            isPermissionGranted = isNotificationServiceEnabled(context)
        }
    }

    // Observe historical alerts from Database
    val donationsList by viewModel.donationsList.collectAsStateWithLifecycle()

    // Clean solid light slate-blue background matching "Professional Polish" theme
    val backgroundColor = Color(0xFFF3F4F9)

    Column(
        modifier = modifier
            .background(backgroundColor)
            .padding(horizontal = 16.dp)
    ) {
        // App Header
        DashboardHeader(
            isPermissionGranted = isPermissionGranted,
            serviceEnabled = viewModel.serviceEnabled.value,
            onToggleService = { viewModel.toggleServiceEnabled(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs / Sections
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Permission Banner Card
            item {
                PermissionCard(
                    isGranted = isPermissionGranted,
                    onRequestPermission = {
                        try {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Webhook Configuration Card
            item {
                WebhookConfigCard(
                    webhookUrl = viewModel.webhookUrl.value,
                    onSaveUrl = {
                        viewModel.updateWebhookUrl(it)
                        Toast.makeText(context, "Webhook URL saved!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Anti-Double Sound Deduplication Info Card
            item {
                DeduplicationEngineCard()
            }

            // Webhook Overlay Testing Card
            item {
                WebhookOverlayTesterCard(
                    onTriggerTest = { name, amount, app ->
                        viewModel.triggerMockTestAlert(name, amount, app) { success, error ->
                            if (success) {
                                Toast.makeText(context, "Test Alert posted successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Alert trigger failed: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Recent Transactions logs Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Donation Alerts History",
                        color = Color(0xFF1B1B1F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    if (donationsList.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearDonationsHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC81E1E))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Logs")
                        }
                    }
                }
            }

            // Real-Time Transaction Logs feed
            if (donationsList.isEmpty()) {
                item {
                    EmptyHistoryState()
                }
            } else {
                items(
                    items = donationsList,
                    key = { it.id }
                ) { donation ->
                    DonationHistoryRow(donation = donation)
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    isPermissionGranted: Boolean,
    serviceEnabled: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    val activeState = isPermissionGranted && serviceEnabled

    // Pulsing animation for the green status light when active
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color(0xFF21005D).copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NotifyAll",
                        color = Color(0xFF21005D),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Stream donation alerts trigger",
                        color = Color(0xFF21005D).copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                // Switch to enable background checking
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (serviceEnabled) "ON" else "OFF",
                        color = if (serviceEnabled) Color(0xFF3F5AA9) else Color(0xFF21005D).copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = serviceEnabled,
                        onCheckedChange = onToggleService,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF3F5AA9),
                            checkedTrackColor = Color(0xFF3F5AA9).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.DarkGray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }

            Divider(color = Color(0xFF21005D).copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeState) Color(0xFF2E7D32).copy(alpha = pulseAlpha)
                            else Color(0xFFC81E1E)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (activeState) "SYSTEM STATUS: RUNNING IN BACKGROUND" else "SYSTEM STATUS: PAUSED",
                    color = if (activeState) Color(0xFF2E7D32) else Color(0xFFC81E1E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFDE8E8)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFF44336).copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC81E1E),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (isGranted) "Notification Access Granted" else "Notification Permission Needed",
                        color = Color(0xFF1B1B1F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isGranted) "NotifyAll is listening for payment notices." else "Tap to grant permission in system settings.",
                        color = if (isGranted) Color(0xFF44474E) else Color(0xFF7D5260),
                        fontSize = 11.sp
                    )
                }
            }

            if (!isGranted) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC81E1E)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("grant_permission_button")
                ) {
                    Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun WebhookConfigCard(
    webhookUrl: String,
    onSaveUrl: (String) -> Unit
) {
    var txtValue by remember(webhookUrl) { mutableStateOf(webhookUrl) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = null,
                    tint = Color(0xFF3F5AA9)
                )
                Text(
                    text = "StreamElements Webhook URL",
                    color = Color(0xFF1B1B1F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Paste your StreamElements webhook dynamic alert URL. This URL is triggered with a POST statement containing the donor details when a notification clears.",
                color = Color(0xFF44474E),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            OutlinedTextField(
                value = txtValue,
                onValueChange = { txtValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("webhook_input"),
                placeholder = {
                    Text(
                        "e.g. https://api.streamelements.com/v2/channel/...",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                },
                textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF1B1B1F), fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3F5AA9),
                    unfocusedBorderColor = Color(0xFFCAC4D0),
                    focusedContainerColor = Color(0xFFF7F2FA),
                    unfocusedContainerColor = Color(0xFFF7F2FA),
                    focusedTextColor = Color(0xFF1B1B1F),
                    unfocusedTextColor = Color(0xFF1B1B1F)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Button(
                onClick = { onSaveUrl(txtValue) },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("save_webhook_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F5AA9)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeduplicationEngineCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star, // Representing shielding/dedup protection
                contentDescription = null,
                tint = Color(0xFF3F5AA9),
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Deduplication Shield: ACTIVE",
                    color = Color(0xFF1B1B1F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Prevents dual audio play. If an app triggers duplicates as it updates notifications, repetitions inside a sliding 6-second window are blocked automatically.",
                    color = Color(0xFF44474E),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun WebhookOverlayTesterCard(
    onTriggerTest: (name: String, amount: String, app: String) -> Unit
) {
    var senderName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf("Paytm") }

    val appList = listOf("Paytm", "PhonePe", "Google Pay", "BHIM")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color(0xFF3F5AA9)
                )
                Text(
                    text = "Test OBS Webhook Alerts Overlay",
                    color = Color(0xFF1B1B1F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Test your StreamElements alerts overlay without spending real money! Type details and click Trigger to simulate an incoming alert on stream.",
                color = Color(0xFF44474E),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Sender Name", fontSize = 11.sp) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF1B1B1F)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3F5AA9),
                        unfocusedBorderColor = Color(0xFFCAC4D0),
                        focusedContainerColor = Color(0xFFF7F2FA),
                        unfocusedContainerColor = Color(0xFFF7F2FA),
                        focusedLabelColor = Color(0xFF3F5AA9),
                        unfocusedLabelColor = Color(0xFF44474E),
                        focusedTextColor = Color(0xFF1B1B1F),
                        unfocusedTextColor = Color(0xFF1B1B1F)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_sender_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)", fontSize = 11.sp) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF1B1B1F)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3F5AA9),
                        unfocusedBorderColor = Color(0xFFCAC4D0),
                        focusedContainerColor = Color(0xFFF7F2FA),
                        unfocusedContainerColor = Color(0xFFF7F2FA),
                        focusedLabelColor = Color(0xFF3F5AA9),
                        unfocusedLabelColor = Color(0xFF44474E),
                        focusedTextColor = Color(0xFF1B1B1F),
                        unfocusedTextColor = Color(0xFF1B1B1F)
                    ),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("test_amount_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Radio Button APP selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Source:",
                    color = Color(0xFF44474E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    appList.forEach { app ->
                        val isSelected = selectedApp == app
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF3F5AA9) else Color(0xFFD9E2FF))
                                .clickable { selectedApp = app }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = app,
                                color = if (isSelected) Color.White else Color(0xFF001945),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val finalName = senderName.trim().ifEmpty { "Sanjay Dev" }
                    val finalAmt = amount.trim().ifEmpty { "100" }
                    onTriggerTest(finalName, finalAmt, selectedApp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trigger_test_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F5AA9)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trigger Test Alert", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF3F5AA9),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No alerts recorded yet",
                color = Color(0xFF1B1B1F),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Send a test alert above, or wait for an incoming money notification to see logs build here.",
                color = Color(0xFF44474E),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun DonationHistoryRow(donation: Donation) {
    val dateString = remember(donation.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(donation.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("donation_row_${donation.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Colorized Icon representing the specific App Provider
                val appColor = when (donation.appName) {
                    "Paytm", "Paytm Business" -> Color(0xFF00B9F5)
                    "PhonePe", "PhonePe Business" -> Color(0xFF5F259F)
                    "Google Pay" -> Color(0xFFEA4335)
                    "BHIM" -> Color(0xFFF9A825)
                    else -> Color(0xFF3F5AA9)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appColor.copy(alpha = 0.12f))
                        .border(1.dp, appColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = donation.appName.take(2).uppercase(),
                        color = appColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(0.68f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = donation.senderName,
                            color = Color(0xFF1B1B1F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = donation.appName,
                            color = Color(0xFF44474E),
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Text(
                            text = dateString,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    if (!donation.errorMessage.isNullOrEmpty()) {
                        Text(
                            text = donation.errorMessage,
                            color = Color(0xFFBA1A1A),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Amount + Transmission Status
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "₹${donation.amount}",
                    color = Color(0xFF2E7D32),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )

                // Small modern badge indicating delivery status to StreamElements overlay
                val (badgeBg, badgeText, statusLabel) = when (donation.webhookStatus) {
                    "SUCCESS" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "SENT")
                    "TEST SUCCESS" -> Triple(Color(0xFFE8EAF6), Color(0xFF3F5AA9), "TESTED")
                    "DEDUPLICATED" -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), "DUPE")
                    else -> Triple(Color(0xFFFFEBEE), Color(0xFFC81E1E), "FAIL")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = badgeText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
