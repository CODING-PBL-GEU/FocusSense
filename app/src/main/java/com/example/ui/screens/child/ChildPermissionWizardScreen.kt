package com.example.ui.screens.child

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.PurpleAccent
import com.example.util.ChildPermissionManager
import com.example.util.ChildPermissionStatus

@Composable
fun ChildPermissionWizardScreen(
    childName: String,
    onContinueToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionStatus by remember {
        mutableStateOf(ChildPermissionManager.checkAllPermissions(context))
    }

    // Automatically recheck permissions whenever the user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = ChildPermissionManager.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Runtime Permission Launchers
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus = ChildPermissionManager.checkAllPermissions(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionStatus = ChildPermissionManager.checkAllPermissions(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Shield & Progress Summary
        item {
            PermissionHeroHeader(
                childName = childName,
                status = permissionStatus,
                onRefresh = {
                    permissionStatus = ChildPermissionManager.checkAllPermissions(context)
                }
            )
        }

        // 2. Step 1: Accessibility Service (AI Sentinel) - CRITICAL
        item {
            PermissionStepCard(
                stepNumber = 1,
                title = "AI Sentinel (Accessibility)",
                tag = "REQUIRED FOR SAFETY",
                tagColor = CoralDanger,
                icon = Icons.Default.Accessibility,
                isGranted = permissionStatus.isAccessibilityEnabled,
                description = "Scrapes live chat messages and web text to detect grooming, cyberbullying, and inappropriate content.",
                instructions = "Tap 'Enable', select FocusSense under Downloaded/Installed Apps, and switch it ON.",
                actionLabel = "Enable Accessibility",
                onAction = {
                    ChildPermissionManager.openAccessibilitySettings(context)
                },
                testTag = "perm_btn_accessibility"
            )
        }

        // 3. Step 2: Usage Access (App Screen Time & Timetable Tracking)
        item {
            PermissionStepCard(
                stepNumber = 2,
                title = "Usage Access (Screen Time)",
                tag = "REQUIRED FOR CURFEW",
                tagColor = IndigoPrimary,
                icon = Icons.Default.DataUsage,
                isGranted = permissionStatus.isUsageAccessGranted,
                description = "Allows FocusSense to know which app is currently active so study timetables and bedtime limits are enforced.",
                instructions = "Tap 'Grant', locate FocusSense in the Usage Access list, and toggle 'Permit usage access'.",
                actionLabel = "Grant Usage Access",
                onAction = {
                    ChildPermissionManager.openUsageAccessSettings(context)
                },
                testTag = "perm_btn_usage"
            )
        }

        // 4. Step 3: Location Access (Live GPS & Emergency Beacon)
        item {
            PermissionStepCard(
                stepNumber = 3,
                title = "Live GPS & Safety Beacon",
                tag = "SAFETY TELEMETRY",
                tagColor = PurpleAccent,
                icon = Icons.Default.LocationOn,
                isGranted = permissionStatus.isLocationGranted,
                description = "Sends live coordinates to the parent dashboard and alerts parents when the child arrives at school or home.",
                instructions = "Grant location access and select 'Allow all the time' for continuous background protection.",
                actionLabel = "Grant Location",
                onAction = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                testTag = "perm_btn_location"
            )
        }

        // 5. Step 4: Battery Saver Exemption (Uninterrupted Background Service)
        item {
            PermissionStepCard(
                stepNumber = 4,
                title = "Battery Optimization Exemption",
                tag = "BACKGROUND SERVICE",
                tagColor = AmberWarning,
                icon = Icons.Default.BatteryChargingFull,
                isGranted = permissionStatus.isBatteryExempted,
                description = "Stops Android OEM task killers from terminating the FocusSense child protection background service.",
                instructions = "Tap 'Exempt' and select 'Allow' on the system dialog.",
                actionLabel = "Exempt from Saver",
                onAction = {
                    ChildPermissionManager.openBatteryOptimizationSettings(context)
                },
                testTag = "perm_btn_battery"
            )
        }

        // 6. Step 5: Screen Overlay (Curfew App Lock)
        item {
            PermissionStepCard(
                stepNumber = 5,
                title = "Curfew App Blocker (Overlay)",
                tag = "RESTRICTION LOCK",
                tagColor = IndigoDark,
                icon = Icons.Default.Layers,
                isGranted = permissionStatus.isOverlayGranted,
                description = "Displays the focus/curfew blocker screen immediately over blocked apps (e.g. TikTok/Games during study hours).",
                instructions = "Allow FocusSense to 'Display over other apps'.",
                actionLabel = "Allow Overlay",
                onAction = {
                    ChildPermissionManager.openOverlaySettings(context)
                },
                testTag = "perm_btn_overlay"
            )
        }

        // 7. Step 6: Notifications
        item {
            PermissionStepCard(
                stepNumber = 6,
                title = "Guardian Alerts & Notifications",
                tag = "NOTIFICATIONS",
                tagColor = EmeraldSafe,
                icon = Icons.Default.Notifications,
                isGranted = permissionStatus.isNotificationGranted,
                description = "Sends the child friendly 10-minute curfews warnings and informs them of active study modes.",
                instructions = "Allow FocusSense to deliver status and curfew notifications.",
                actionLabel = "Allow Notifications",
                onAction = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                testTag = "perm_btn_notifications"
            )
        }

        // 8. Bottom Action
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (permissionStatus.allCrucialGranted) EmeraldSafeBg else AmberWarningBg
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (permissionStatus.allCrucialGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (permissionStatus.allCrucialGranted) EmeraldSafe else AmberWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (permissionStatus.isFullyProtected) {
                                "Shield Fully Active & Armed!"
                            } else if (permissionStatus.allCrucialGranted) {
                                "Core Protection Ready (${permissionStatus.grantedCount}/${permissionStatus.totalCount})"
                            } else {
                                "Complete Setup for Full Protection"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (permissionStatus.allCrucialGranted) EmeraldSafe else AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onContinueToDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (permissionStatus.allCrucialGranted) EmeraldSafe else IndigoPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("perm_btn_continue")
                    ) {
                        Text(
                            text = if (permissionStatus.isFullyProtected) {
                                "Open Child Companion Dashboard"
                            } else {
                                "Continue to Dashboard"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionHeroHeader(
    childName: String,
    status: ChildPermissionStatus,
    onRefresh: () -> Unit
) {
    val progressColor by animateColorAsState(
        targetValue = when {
            status.isFullyProtected -> EmeraldSafe
            status.allCrucialGranted -> IndigoPrimary
            else -> AmberWarning
        },
        label = "progressColor"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(progressColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device Protection Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Guarding $childName's Phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("perm_btn_refresh")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${status.grantedCount} of ${status.totalCount} Permissions Active",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = progressColor
                )
                Text(
                    text = "${(status.progressPercent * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { status.progressPercent },
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Android requires explicit user permissions so FocusSense can run in the background, guard chats, and enforce screen-time limits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: Int,
    title: String,
    tag: String,
    tagColor: Color,
    icon: ImageVector,
    isGranted: Boolean,
    description: String,
    instructions: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isGranted) EmeraldSafe.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGranted) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Step $stepNumber: $title",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Surface(
                            color = tagColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                color = tagColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Status Badge
                Surface(
                    color = if (isGranted) EmeraldSafeBg else AmberWarningBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isGranted) EmeraldSafe else AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGranted) "Active" else "Required",
                            color = if (isGranted) EmeraldSafe else AmberWarning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Guidance hint
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = instructions,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isGranted) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tagColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag(testTag)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("${testTag}_reconfigure")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSafe,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configured (Tap to modify in Settings)", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
