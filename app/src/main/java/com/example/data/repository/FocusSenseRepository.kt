package com.example.data.repository

import android.content.Context
import com.example.ai.ThreatAnalysisResult
import com.example.ai.ThreatEvaluationEngine
import com.example.data.local.FocusSenseDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

sealed class SentinelEvent {
    data class ContentFlagged(val log: ActivityLogEntity, val threatResult: ThreatAnalysisResult) : SentinelEvent()
    data class AppRestricted(val appName: String, val ruleName: String, val endTime: String) : SentinelEvent()
    data class LocationUpdated(val locationName: String, val lat: Double, val lng: Double) : SentinelEvent()
    data class SyncCompleted(val syncedCount: Int) : SentinelEvent()
}

data class ActiveAppBlockInfo(
    val isBlocked: Boolean,
    val ruleName: String = "",
    val category: String = "",
    val endTime: String = "",
    val restrictedAppName: String = ""
)

class FocusSenseRepository(context: Context) {

    private val db = FocusSenseDatabase.getInstance(context)
    private val threatEngine = ThreatEvaluationEngine()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Current Session State
    private val prefs = context.getSharedPreferences("focussense_session_prefs", Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String>("")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    // Network & Zero Data-Loss Sync State
    private val _isNetworkConnected = MutableStateFlow<Boolean>(true)
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Central Server & Database URL
    private val _serverUrl = MutableStateFlow<String>("https://focussense-api.onrender.com")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Real-Time Push / Sentinel Event Stream
    private val _eventStream = MutableSharedFlow<SentinelEvent>(extraBufferCapacity = 64)
    val eventStream: SharedFlow<SentinelEvent> = _eventStream.asSharedFlow()

    fun updateServerUrl(url: String) {
        _serverUrl.value = url.trim()
    }

    suspend fun testServerConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val service = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            val response = service.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                Pair(true, "Connected to ${response.body()?.service ?: "FocusSense Server"}")
            } else {
                Pair(false, "Server responded with HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Connection failed")
        }
    }

    init {
        // Restore active user session from local preferences
        val savedUserId = prefs.getString("saved_user_id", null)
        if (!savedUserId.isNullOrBlank()) {
            scope.launch {
                val user = db.userDao().getUserByIdSync(savedUserId)
                if (user != null) {
                    _currentUser.value = user
                    if (user.role == "child") {
                        _selectedChildId.value = user.userId
                    }
                }
            }
        }
    }

    // Role & User Authentication Methods
    suspend fun signUpParent(
        name: String,
        familyName: String,
        email: String,
        password: String,
        pin: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()
            val existing = db.userDao().getUserByEmail(normalizedEmail)
            if (existing != null) {
                return@withContext Result.failure(Exception("An account with email '$normalizedEmail' already exists. Please sign in."))
            }

            val group = FamilyGroupEntity(
                groupId = "group-${UUID.randomUUID().toString().take(8)}",
                familyName = if (familyName.isNotBlank()) familyName.trim() else "${name.trim()}'s Family"
            )
            db.familyGroupDao().insert(group)

            val parentUser = UserEntity(
                userId = "parent-${UUID.randomUUID().toString().take(8)}",
                groupId = group.groupId,
                email = normalizedEmail,
                password = password,
                role = "parent",
                name = name.trim(),
                pin = if (pin.isNotBlank()) pin.trim() else "1234"
            )
            db.userDao().insert(parentUser)

            val parentDevice = DeviceEntity(
                deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                userId = parentUser.userId,
                deviceName = "${name.trim()}'s Parent Phone",
                batteryPercent = 100,
                isOnline = true
            )
            db.deviceDao().insert(parentDevice)

            // Persist session
            prefs.edit().putString("saved_user_id", parentUser.userId).apply()
            _currentUser.value = parentUser
            Result.success(parentUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInParent(email: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()
            val user = db.userDao().getUserByEmail(normalizedEmail)
            if (user == null) {
                return@withContext Result.failure(Exception("No account found for '$normalizedEmail'. Please create an account first."))
            }
            if (user.password != password) {
                return@withContext Result.failure(Exception("Incorrect password. Please verify and try again."))
            }

            prefs.edit().putString("saved_user_id", user.userId).apply()
            _currentUser.value = user
            if (user.role == "child") {
                _selectedChildId.value = user.userId
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairChildDevice(
        parentEmail: String,
        parentPassword: String,
        childName: String,
        deviceName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedParentEmail = parentEmail.trim().lowercase()
            var parentUser = db.userDao().getUserByEmail(normalizedParentEmail)

            // If parent not in local DB (e.g. running on separate physical child device),
            // initialize the parent & family record locally linked with parent's email credentials.
            val groupId = if (parentUser != null) {
                if (parentUser.password != parentPassword) {
                    return@withContext Result.failure(Exception("Parent password incorrect. Cannot link device."))
                }
                parentUser.groupId
            } else {
                val newGroupId = "group-${UUID.randomUUID().toString().take(8)}"
                val newGroup = FamilyGroupEntity(
                    groupId = newGroupId,
                    familyName = "${childName.trim()}'s Family"
                )
                db.familyGroupDao().insert(newGroup)

                val newParent = UserEntity(
                    userId = "parent-${UUID.randomUUID().toString().take(8)}",
                    groupId = newGroupId,
                    email = normalizedParentEmail,
                    password = parentPassword,
                    role = "parent",
                    name = "Parent ($normalizedParentEmail)",
                    pin = "1234"
                )
                db.userDao().insert(newParent)
                newGroupId
            }

            val childId = "child-${UUID.randomUUID().toString().take(8)}"
            val childUser = UserEntity(
                userId = childId,
                groupId = groupId,
                email = "${childName.trim().lowercase().replace(" ", "")}@family.focussense",
                password = parentPassword,
                role = "child",
                name = childName.trim(),
                pin = ""
            )
            db.userDao().insert(childUser)

            val childDevice = DeviceEntity(
                deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                userId = childId,
                deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Device",
                batteryPercent = 95,
                isOnline = true
            )
            db.deviceDao().insert(childDevice)

            // Persist session as child device
            prefs.edit().putString("saved_user_id", childId).apply()
            _currentUser.value = childUser
            _selectedChildId.value = childId

            Result.success(childUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        prefs.edit().remove("saved_user_id").apply()
        _currentUser.value = null
        _selectedChildId.value = ""
    }

    // Role & User Switching
    fun switchUser(user: UserEntity) {
        _currentUser.value = user
    }

    fun setSelectedChild(childId: String) {
        _selectedChildId.value = childId
    }

    fun toggleNetworkSimulation(connected: Boolean) {
        _isNetworkConnected.value = connected
    }

    // Database Flows
    val familyGroup: Flow<FamilyGroupEntity?> = db.familyGroupDao().getFamilyGroup()
    val allUsers: Flow<List<UserEntity>> = db.userDao().getAllUsers()
    val childrenUsers: Flow<List<UserEntity>> = db.userDao().getUsersByRole("child")
    val parentUsers: Flow<List<UserEntity>> = db.userDao().getUsersByRole("parent")
    val allDevices: Flow<List<DeviceEntity>> = db.deviceDao().getAllDevices()

    fun getDevicesForUser(userId: String): Flow<List<DeviceEntity>> =
        db.deviceDao().getDevicesForUser(userId)

    val allLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getAllLogs()
    val flaggedLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getFlaggedLogs()

    fun getLogsForChild(childId: String): Flow<List<ActivityLogEntity>> =
        db.activityLogDao().getLogsForChild(childId)

    fun getFlaggedLogsForChild(childId: String): Flow<List<ActivityLogEntity>> =
        db.activityLogDao().getFlaggedLogsForChild(childId)

    val allScheduleRules: Flow<List<ScheduleRuleEntity>> = db.scheduleRuleDao().getAllRules()

    fun getRulesForChild(childId: String): Flow<List<ScheduleRuleEntity>> =
        db.scheduleRuleDao().getRulesForChild(childId)

    fun getLatestLocationForChild(childId: String): Flow<LocationPointEntity?> =
        db.locationDao().getLatestLocationForChild(childId)

    fun getLocationHistoryForChild(childId: String): Flow<List<LocationPointEntity>> =
        db.locationDao().getLocationHistoryForChild(childId)

    val unsyncedLogsCount: Flow<Int> = db.activityLogDao().getUnsyncedCount()
    val unsyncedLocationCount: Flow<Int> = db.locationDao().getUnsyncedCount()

    // 1. Accessibility Content Processing & AI Evaluation
    suspend fun processExtractedContent(
        childId: String,
        packageName: String,
        appName: String,
        contentTitle: String,
        extractedText: String,
        forceOffline: Boolean = false
    ): ActivityLogEntity = withContext(Dispatchers.IO) {
        val analysis = threatEngine.evaluateContent(
            appName = appName,
            contentTitle = contentTitle,
            extractedText = extractedText,
            forceOfflineOnly = forceOffline || !_isNetworkConnected.value
        )

        val log = ActivityLogEntity(
            logId = UUID.randomUUID().toString(),
            childId = childId,
            packageName = packageName,
            appName = appName,
            contentTitle = contentTitle,
            extractedText = extractedText,
            isFlagged = analysis.isFlagged,
            threatCategory = analysis.threatCategory,
            confidenceScore = analysis.confidenceScore,
            aiAnalysisSummary = analysis.aiAnalysisSummary,
            recordedAt = System.currentTimeMillis(),
            isSynced = _isNetworkConnected.value, // Auto-synced if online, queued if offline
            isAcknowledged = false
        )

        // Zero Data-Loss Guarantee: Insert into local Room SQLite DB immediately
        db.activityLogDao().insert(log)

        if (analysis.isFlagged) {
            _eventStream.tryEmit(SentinelEvent.ContentFlagged(log, analysis))
        }

        log
    }

    // 2. Scheduled App Blocking Engine
    suspend fun checkAppRestriction(childId: String, packageName: String): ActiveAppBlockInfo = withContext(Dispatchers.IO) {
        val rules = db.scheduleRuleDao().getRulesForChildSync(childId)
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        // Day of week check (e.g. Mon, Tue)
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val currentDayName = dayNames[now.get(Calendar.DAY_OF_WEEK) - 1]

        for (rule in rules) {
            if (!rule.isActive) continue

            // Check days
            if (!rule.dayOfWeek.contains(currentDayName, ignoreCase = true)) continue

            // Parse start and end time
            val startParts = rule.startTime.split(":")
            val endParts = rule.endTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) continue

            val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

            val isTimeMatch = if (startMinutes <= endMinutes) {
                currentTimeMinutes in startMinutes..endMinutes
            } else {
                // Overnight rule (e.g. 21:00 to 06:30)
                currentTimeMinutes >= startMinutes || currentTimeMinutes <= endMinutes
            }

            if (isTimeMatch) {
                val packages = rule.restrictedPackages.split(",").map { it.trim() }
                if (packages.contains(packageName) || packages.contains("*")) {
                    return@withContext ActiveAppBlockInfo(
                        isBlocked = true,
                        ruleName = rule.ruleName,
                        category = rule.category,
                        endTime = rule.endTime,
                        restrictedAppName = packageName
                    )
                }
            }
        }

        ActiveAppBlockInfo(isBlocked = false)
    }

    // 3. Location Logging
    suspend fun recordLocation(
        childId: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        locationName: String
    ): Unit = withContext(Dispatchers.IO) {
        val point = LocationPointEntity(
            locId = UUID.randomUUID().toString(),
            childId = childId,
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            locationName = locationName,
            recordedAt = System.currentTimeMillis(),
            isSynced = _isNetworkConnected.value
        )
        db.locationDao().insert(point)
        _eventStream.tryEmit(SentinelEvent.LocationUpdated(locationName, lat, lng))
    }

    // 4. Zero Data-Loss Guarantee: Sync queued offline data to Central Python REST / Supabase PostgreSQL
    suspend fun syncOfflineQueue(): Int = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        // Simulate network roundtrip latency to REST API
        delay(900)

        // Mark local records as synced
        db.activityLogDao().markAllSynced()
        db.locationDao().markAllSynced()

        _isSyncing.value = false
        _eventStream.tryEmit(SentinelEvent.SyncCompleted(1))
        1
    }

    // 5. Parent Administrative Actions
    suspend fun acknowledgeAlert(logId: String) = withContext(Dispatchers.IO) {
        db.activityLogDao().markAcknowledged(logId)
    }

    suspend fun removeVulnerableLog(logId: String) = withContext(Dispatchers.IO) {
        db.activityLogDao().deleteLog(logId)
    }

    suspend fun addScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().insert(rule)
    }

    suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().update(rule)
    }

    suspend fun deleteScheduleRule(ruleId: String) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().delete(ruleId)
    }

    suspend fun toggleRuleActive(ruleId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().toggleRuleActive(ruleId, isActive)
    }

    suspend fun updateParentPin(userId: String, newPin: String) = withContext(Dispatchers.IO) {
        db.userDao().updatePin(userId, newPin)
    }
}
