package com.example.ui.screens.child

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLogEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.PREDEFINED_RESTRICTED_APPS
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.ui.components.ThreatCategoryBadge
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

@Composable
fun ChildDashboardScreen(
    currentChild: UserEntity?,
    scheduleRules: List<ScheduleRuleEntity>,
    latestLocation: LocationPointEntity?,
    isAnalyzing: Boolean,
    lastSimulatedLog: ActivityLogEntity?,
    onSimulateContentExtraction: (appName: String, title: String, text: String, pkg: String) -> Unit,
    onSimulateRestrictedLaunch: (pkg: String, name: String) -> Unit,
    onDismissSimulatedLog: () -> Unit,
    onRequestParentUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "My Schedule") },
                    label = { Text("My Schedule", fontSize = 11.sp) },
                    modifier = Modifier.testTag("child_tab_schedule")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "AI Sentinel") },
                    label = { Text("AI Sentinel", fontSize = 11.sp) },
                    modifier = Modifier.testTag("child_tab_sentinel")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.HourglassTop, contentDescription = "Focus Shield") },
                    label = { Text("Focus Shield", fontSize = 11.sp) },
                    modifier = Modifier.testTag("child_tab_shield")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Safety Beacon") },
                    label = { Text("Safety Beacon", fontSize = 11.sp) },
                    modifier = Modifier.testTag("child_tab_beacon")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChildScheduleTab(
                    childName = currentChild?.name ?: "Student",
                    rules = scheduleRules
                )
                1 -> ChildSentinelSandboxTab(
                    isAnalyzing = isAnalyzing,
                    lastLog = lastSimulatedLog,
                    onSimulate = onSimulateContentExtraction,
                    onDismissLog = onDismissSimulatedLog
                )
                2 -> ChildFocusShieldTab(
                    rules = scheduleRules,
                    onLaunchRestrictedApp = onSimulateRestrictedLaunch
                )
                3 -> ChildSafetyBeaconTab(
                    childName = currentChild?.name ?: "Student",
                    latestLocation = latestLocation
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: CHILD SCHEDULE & ACTIVE TIMETABLE (TAMPER RESISTANT)
// -------------------------------------------------------------
@Composable
private fun ChildScheduleTab(
    childName: String,
    rules: List<ScheduleRuleEntity>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Child Welcome Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Welcome back, $childName!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "FocusSense Healthy Habit Schedule",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked by Parent",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Parent Locked",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Focus Mode active: 3 apps paused during afternoon study window.",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Today's Timetable",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (rules.isEmpty()) {
            item {
                Text(
                    text = "No timetable rules assigned yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            items(rules, key = { it.ruleId }) { rule ->
                ChildRuleCard(rule = rule)
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Children cannot modify or disable this schedule. Changes must be made from a parent device with the security PIN.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildRuleCard(rule: ScheduleRuleEntity) {
    val apps = rule.restrictedPackages.split(",").filter { it.isNotBlank() }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.ruleName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (rule.isActive) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (rule.isActive) "Active Rule" else "Paused",
                        color = if (rule.isActive) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${rule.startTime} - ${rule.endTime} • ${rule.category}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = IndigoPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Paused Apps (${apps.size}):",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                apps.forEach { pkg ->
                    val name = when {
                        pkg.contains("youtube") -> "YouTube"
                        pkg.contains("tiktok") || pkg.contains("musically") -> "TikTok"
                        pkg.contains("roblox") -> "Roblox"
                        pkg.contains("instagram") -> "Instagram"
                        pkg.contains("discord") -> "Discord"
                        else -> pkg.substringAfterLast('.')
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: AI SENTINEL & ACCESSIBILITY SCRAPING SIMULATOR
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildSentinelSandboxTab(
    isAnalyzing: Boolean,
    lastLog: ActivityLogEntity?,
    onSimulate: (String, String, String, String) -> Unit,
    onDismissLog: () -> Unit
) {
    var customText by remember { mutableStateOf("") }
    var customAppName by remember { mutableStateOf("Chrome Browser") }

    val presets = listOf(
        Triple("Discord Chat", "Stranger solicitation test", "Hey are you alone? Don't tell your parents, meet me at the skatepark behind school at 6pm"),
        Triple("Google Search", "Academic cheating test", "how to bypass turnitin write my 6th grade history essay free online bot hack"),
        Triple("YouTube", "Educational science test", "Photosynthesis and cellular respiration in plant cells Khan Academy high school"),
        Triple("Instagram DM", "Cyberbullying test", "Nobody likes you in 7th grade just disappear and leave our group chat"),
        Triple("Duolingo", "Language learning test", "Complete daily streak: Spanish vocabulary practice - ¿Dónde está el mercado central?")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "On-Device Content Sentinel",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AccessibilityService extracts contextual text; on-device MobileBERT or Gemini AI classifies vulnerabilities in real-time.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sentinel Active Status Indicator
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EmeraldSafeBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(EmeraldSafe)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Accessibility Sentinel Service: ACTIVE",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSafe,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Zero Data-Loss active: logs persist locally in Room SQLite.",
                            fontSize = 11.sp,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }
        }

        // Live Simulated Evaluation Card
        lastLog?.let { log ->
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (log.isFlagged) CoralDangerBg else EmeraldSafeBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_result_banner")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Classification Result",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (log.isFlagged) CoralDanger else EmeraldSafe
                            )
                            ThreatCategoryBadge(
                                category = log.threatCategory,
                                confidence = log.confidenceScore
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "\"${log.extractedText}\"",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "AI Rationale: ${log.aiAnalysisSummary}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismissLog) {
                                Text("Dismiss Result", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Quick Test Presets
        item {
            Text(
                text = "Simulate Real-Time In-App Text Scenarios",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(presets) { preset ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSimulate(preset.first, preset.second, preset.third, "com.simulation.${preset.first.lowercase().take(4)}")
                    }
                    .testTag("preset_${preset.first.replace(" ", "_")}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.first,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = preset.second,
                            fontSize = 11.sp,
                            color = IndigoPrimary
                        )
                        Text(
                            text = "\"${preset.third}\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Custom Text Scraper Test
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Custom Text Scrape Simulator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text("Type custom chat, search query, or forum post...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_scrape_text_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (customText.isNotBlank()) {
                                onSimulate("Custom App", "Manual Simulated Input", customText, "com.custom.sample")
                                customText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("simulate_custom_text_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Simulate Scraping & Classify")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: FOCUS SHIELD & APP RESTRICTION TESTER
// -------------------------------------------------------------
@Composable
private fun ChildFocusShieldTab(
    rules: List<ScheduleRuleEntity>,
    onLaunchRestrictedApp: (pkg: String, name: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Focus Shield & App Restrictor",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UsageStatsManager + SYSTEM_ALERT_WINDOW simulation: try launching an app to see the Focus overlay in action.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                text = "Tap any restricted app to simulate opening it:",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(PREDEFINED_RESTRICTED_APPS) { app ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchRestrictedApp(app.packageName, app.appName) }
                    .testTag("app_launch_${app.appName}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IndigoPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = app.appName.take(1),
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = app.category,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onLaunchRestrictedApp(app.packageName, app.appName) }
                    ) {
                        Text("Test Launch", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: SAFETY BEACON & CHILD LOCATION STATUS
// -------------------------------------------------------------
@Composable
private fun ChildSafetyBeaconTab(
    childName: String,
    latestLocation: LocationPointEntity?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Physical Safety Beacon",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "FusedLocationProviderClient runs continuously in a low-power ForegroundService.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EmeraldSafe.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = EmeraldSafe,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Safe Zone Verified",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = latestLocation?.locationName ?: "Home (Safe Haven)",
                                fontSize = 13.sp,
                                color = EmeraldSafe,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Parents have real-time visibility to ensure physical safety on the way to and from school.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Emergency Information",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If you ever feel unsafe or need a ride, your emergency contacts (Mom & Dad) receive direct alerts with high-precision GPS coordinates.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
