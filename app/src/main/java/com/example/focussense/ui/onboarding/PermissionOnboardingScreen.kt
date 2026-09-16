package com.example.focussense.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionOnboardingScreen(
    accessibilityServiceClass: Class<*>, // Pass your Accessibility service class reference here
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe permission states dynamically
    var hasUsageStats by remember { mutableStateOf(ServiceCheckUtils.isUsageStatsPermissionGranted(context)) }
    var hasAccessibility by remember { mutableStateOf(ServiceCheckUtils.isAccessibilityServiceEnabled(context, accessibilityServiceClass)) }
    var hasNotification by remember { mutableStateOf(ServiceCheckUtils.isNotificationPermissionGranted(context)) }

    // Launcher for Android 13+ Notification Runtime Prompt
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotification = isGranted
    }

    // Re-verify states whenever user returns back to the application from settings window
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStats = ServiceCheckUtils.isUsageStatsPermissionGranted(context)
                hasAccessibility = ServiceCheckUtils.isAccessibilityServiceEnabled(context, accessibilityServiceClass)
                hasNotification = ServiceCheckUtils.isNotificationPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Direct user forward if everything is fully enabled
    LaunchedEffect(hasUsageStats, hasAccessibility, hasNotification) {
        if (hasUsageStats && hasAccessibility && hasNotification) {
            onAllPermissionsGranted()
        }
    }

    // Onboarding UI Layout Setup
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Setup Required Services",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Step 1: Usage Stats
        if (!hasUsageStats) {
            PermissionStepRow(
                title = "1. Enable Usage Statistics",
                description = "Required to detect which distracting application is opened.",
                buttonText = "Grant Usage Access",
                onClick = { ServiceIntentUtils.openUsageStatsSettings(context) }
            )
        }
        // Step 2: Accessibility
        else if (!hasAccessibility) {
            PermissionStepRow(
                title = "2. Enable Accessibility Service",
                description = "Required to lock applications out and overlay intercept blocking UI.",
                buttonText = "Enable Service",
                onClick = { ServiceIntentUtils.openAccessibilitySettings(context) }
            )
        }
        // Step 3: Notifications
        else if (!hasNotification) {
            PermissionStepRow(
                title = "3. Enable Notifications",
                description = "Required to maintain persistent app blocking active in the background.",
                buttonText = "Allow Notifications",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        ServiceIntentUtils.openNotificationSettings(context)
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionStepRow(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onClick) {
            Text(text = buttonText)
        }
    }
}