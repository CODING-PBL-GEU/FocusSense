package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.ai.ThreatAnalysisResult
import com.example.ai.ThreatEvaluationEngine
import com.example.data.local.FocusSenseDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.util.InstalledAppScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.example.receiver.FocusSenseAdminReceiver
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    private val appContext = context.applicationContext
    private val db = FocusSenseDatabase.getInstance(appContext)
    private val threatEngine = ThreatEvaluationEngine()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Current Session State
    private val prefs = context.getSharedPreferences("focussense_session_prefs", Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isSessionReady = MutableStateFlow<Boolean>(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String>("")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    // Network & Zero Data-Loss Sync State
    private val _isNetworkConnected = MutableStateFlow<Boolean>(true)
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Central Server & Database URL (Render + Supabase PostgreSQL)
    private val _serverUrl = MutableStateFlow<String>(
        prefs.getString("server_api_url", "https://parental-controll.onrender.com") ?: "https://parental-controll.onrender.com"
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Real-Time Push / Sentinel Event Stream
    private val _eventStream = MutableSharedFlow<SentinelEvent>(extraBufferCapacity = 64)
    val eventStream: SharedFlow<SentinelEvent> = _eventStream.asSharedFlow()

    fun updateServerUrl(url: String) {
        val cleanUrl = url.trim()
        _serverUrl.value = cleanUrl
        prefs.edit().putString("server_api_url", cleanUrl).apply()
    }

    suspend fun testServerConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val service = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            val response = service.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val dbStatus = body.database ?: "connected"
                val counts = body.supabase_counts
                val countsSummary = if (counts != null && counts.isNotEmpty()) {
                    " (Users: ${counts["users"] ?: 0}, Logs: ${counts["activity_logs"] ?: 0}, Locations: ${counts["location_points"] ?: 0})"
                } else ""
                Pair(true, "Connected: ${body.service} • DB: $dbStatus$countsSummary")
            } else {
                Pair(false, "Server responded with HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Connection failed")
        }
    }

    // In-Memory App Blocking Cache for 0ms Latency in AccessibilityService
    private val _instantBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val instantBlockedPackages: StateFlow<Set<String>> = _instantBlockedPackages.asStateFlow()

    private val _temporaryUnlockedPackages = ConcurrentHashMap<String, Long>()

    // Pomodoro Child Focus Lock State
    data class PomodoroState(
        val packageName: String,
        val appName: String,
        val durationMinutes: Int,
        val remainingSeconds: Long,
        val isRunning: Boolean
    )

    private val _pomodoroState = MutableStateFlow<PomodoroState?>(null)
    val pomodoroState: StateFlow<PomodoroState?> = _pomodoroState.asStateFlow()

    private var pomodoroJob: Job? = null

    fun startPomodoro(packageName: String, appName: String, durationMinutes: Int) {
        pomodoroJob?.cancel()
        val totalSecs = (durationMinutes * 60).toLong()
        _pomodoroState.value = PomodoroState(
            packageName = packageName.lowercase(),
            appName = appName,
            durationMinutes = durationMinutes,
            remainingSeconds = totalSecs,
            isRunning = true
        )

        pomodoroJob = CoroutineScope(Dispatchers.IO).launch {
            var currentSecs = totalSecs
            while (currentSecs > 0 && _pomodoroState.value?.isRunning == true) {
                delay(1000)
                currentSecs--
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = currentSecs)
            }
            if (currentSecs <= 0) {
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = 0, isRunning = false)
            }
        }
    }

    fun pausePomodoro() {
        _pomodoroState.value = _pomodoroState.value?.copy(isRunning = false)
        pomodoroJob?.cancel()
    }

    fun resumePomodoro() {
        val current = _pomodoroState.value ?: return
        if (current.remainingSeconds <= 0) return
        _pomodoroState.value = current.copy(isRunning = true)

        pomodoroJob?.cancel()
        pomodoroJob = CoroutineScope(Dispatchers.IO).launch {
            var currentSecs = current.remainingSeconds
            while (currentSecs > 0 && _pomodoroState.value?.isRunning == true) {
                delay(1000)
                currentSecs--
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = currentSecs)
            }
            if (currentSecs <= 0) {
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = 0, isRunning = false)
            }
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        _pomodoroState.value = null
    }

    fun isPackageBlockedSync(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val tempExpiry = _temporaryUnlockedPackages[lower] ?: 0L
        if (tempExpiry > System.currentTimeMillis()) {
            return false
        }
        return _instantBlockedPackages.value.any { it.equals(lower, ignoreCase = true) }
    }

    fun temporarilyUnlockApp(packageName: String, minutes: Int = 10) {
        val lower = packageName.lowercase()
        _temporaryUnlockedPackages[lower] = System.currentTimeMillis() + (minutes * 60 * 1000L)
    }

    fun isPackageTemporarilyUnlocked(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val tempExpiry = _temporaryUnlockedPackages[lower] ?: 0L
        return tempExpiry > System.currentTimeMillis()
    }

    private val _recentLoggedThreats = ConcurrentHashMap<String, Long>()
    private val _antiTamperDisabledUntil = MutableStateFlow(0L)

    fun isAntiTamperTemporarilyDisabled(): Boolean {
        return _antiTamperDisabledUntil.value > System.currentTimeMillis()
    }

    fun disableAntiTamperForUninstall(minutes: Int = 10) {
        _antiTamperDisabledUntil.value = System.currentTimeMillis() + (minutes * 60 * 1000L)
    }

    fun deactivateDeviceAdminAndUninstall(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, FocusSenseAdminReceiver::class.java)
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
            }
            disableAntiTamperForUninstall(15)
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(uninstallIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun cleanupOldLocations() = withContext(Dispatchers.IO) {
        // Retain 4 months of location history (120 days = 10,368,000,000 ms)
        val fourMonthsAgo = System.currentTimeMillis() - (120L * 24 * 60 * 60 * 1000L)
        db.locationDao().deleteLocationsOlderThan(fourMonthsAgo)
    }

    init {
        scope.launch {
            // 1. Permanently remove any legacy hardcoded demo users/groups
            try {
                db.userDao().deleteDemoUsers()
                db.familyGroupDao().deleteDemoGroup()
            } catch (_: Exception) {}

            // 2. Automatically enforce 4-month location retention policy
            try {
                cleanupOldLocations()
            } catch (_: Exception) {}

            // 3. Restore authenticated active user session from local preferences
            val savedUserId = prefs.getString("saved_user_id", null)
            var activeUser: UserEntity? = null
            if (!savedUserId.isNullOrBlank()) {
                activeUser = db.userDao().getUserByIdSync(savedUserId)
            }

            if (activeUser == null) {
                val existingParents = db.userDao().getUsersByRoleSync("parent")
                if (existingParents.isNotEmpty()) {
                    activeUser = existingParents.first()
                }
            }

            if (activeUser != null) {
                _currentUser.value = activeUser
                prefs.edit().putString("saved_user_id", activeUser.userId).apply()
                if (activeUser.role == "child") {
                    _selectedChildId.value = activeUser.userId
                } else {
                    val children = db.userDao().getChildrenByGroupSync(activeUser.groupId)
                    if (children.isNotEmpty()) {
                        _selectedChildId.value = children.first().userId
                    }
                }
            }

            // Initialize in-memory blocked apps cache from DB
            try {
                val blocked = db.installedAppDao().getBlockedPackageNamesSync()
                _instantBlockedPackages.value = blocked.map { it.lowercase() }.toSet()
            } catch (_: Exception) {}

            _isSessionReady.value = true
        }
    }

    suspend fun launchDemoParent() = withContext(Dispatchers.IO) {
        val parent = db.userDao().getUsersByRoleSync("parent").firstOrNull()
        if (parent != null) {
            _currentUser.value = parent
            prefs.edit().putString("saved_user_id", parent.userId).apply()
            val children = db.userDao().getUsersByRoleSync("child")
            if (children.isNotEmpty()) {
                _selectedChildId.value = children.first().userId
            }
        }
    }

    suspend fun launchDemoChild() = withContext(Dispatchers.IO) {
        val child = db.userDao().getUsersByRoleSync("child").firstOrNull()
        if (child != null) {
            _currentUser.value = child
            prefs.edit().putString("saved_user_id", child.userId).apply()
            _selectedChildId.value = child.userId
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

            // 1. Attempt to register on Central Server (Render / Supabase)
            var serverParentUser: UserEntity? = null
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.registerParent(
                    com.example.data.remote.RegisterParentDto(
                        name = name.trim(),
                        family_name = familyName.trim(),
                        email = normalizedEmail,
                        password = password,
                        pin = if (pin.isNotBlank()) pin.trim() else "1234"
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val group = FamilyGroupEntity(
                        groupId = body.user.group_id,
                        familyName = body.family_name.ifBlank { "${name.trim()}'s Family" }
                    )
                    db.familyGroupDao().insert(group)

                    val parent = UserEntity(
                        userId = body.user.user_id,
                        groupId = body.user.group_id,
                        email = body.user.email,
                        password = password,
                        role = body.user.role,
                        name = body.user.name,
                        pin = body.user.pin,
                        avatar = body.user.avatar
                    )
                    db.userDao().insert(parent)
                    serverParentUser = parent
                }
            } catch (e: Exception) {
                // Server unavailable or offline -> fallback to local creation below
            }

            if (serverParentUser != null) {
                prefs.edit().putString("saved_user_id", serverParentUser.userId).apply()
                _currentUser.value = serverParentUser
                return@withContext Result.success(serverParentUser)
            }

            // 2. Offline / Local Fallback
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

            // 1. Attempt Server Login (Render / Supabase)
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.loginParent(
                    com.example.data.remote.LoginParentDto(
                        email = normalizedEmail,
                        password = password
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val group = FamilyGroupEntity(
                        groupId = body.user.group_id,
                        familyName = body.family_name.ifBlank { "My Family" }
                    )
                    db.familyGroupDao().insert(group)

                    val parent = UserEntity(
                        userId = body.user.user_id,
                        groupId = body.user.group_id,
                        email = body.user.email,
                        password = password,
                        role = body.user.role,
                        name = body.user.name,
                        pin = body.user.pin,
                        avatar = body.user.avatar
                    )
                    db.userDao().insert(parent)

                    // Insert synced children
                    val children = body.children.map { c ->
                        UserEntity(
                            userId = c.user_id,
                            groupId = c.group_id,
                            email = c.email,
                            password = password,
                            role = c.role,
                            name = c.name,
                            pin = c.pin,
                            avatar = c.avatar
                        )
                    }
                    if (children.isNotEmpty()) {
                        db.userDao().insertAll(children)
                        _selectedChildId.value = children.first().userId
                    }

                    prefs.edit().putString("saved_user_id", parent.userId).apply()
                    _currentUser.value = parent
                    return@withContext Result.success(parent)
                }
            } catch (e: Exception) {
                // Server unavailable or offline -> fallback to local check
            }

            // 2. Offline / Local fallback check
            val user = db.userDao().getUserByEmail(normalizedEmail)
            if (user == null) {
                return@withContext Result.failure(Exception("No account found for '$normalizedEmail'. Please verify credentials or create an account."))
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

            // 1. Attempt Server Device Pairing (Render / Supabase)
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.pairChildDevice(
                    com.example.data.remote.PairChildDto(
                        parent_email = normalizedParentEmail,
                        parent_password = parentPassword,
                        child_name = childName.trim(),
                        device_name = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone"
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val childUser = UserEntity(
                        userId = body.child_user.user_id,
                        groupId = body.group_id,
                        email = body.child_user.email,
                        password = parentPassword,
                        role = "child",
                        name = body.child_user.name.ifBlank { childName.trim() },
                        pin = ""
                    )
                    db.userDao().insert(childUser)

                    val childDevice = DeviceEntity(
                        deviceId = body.device_id,
                        userId = childUser.userId,
                        deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone",
                        batteryPercent = 95,
                        isOnline = true
                    )
                    db.deviceDao().insert(childDevice)

                    prefs.edit().putString("saved_user_id", childUser.userId).apply()
                    _currentUser.value = childUser
                    _selectedChildId.value = childUser.userId
                    return@withContext Result.success(childUser)
                }
            } catch (e: Exception) {
                // Server unavailable or offline -> fallback to local creation
            }

            // 2. Offline Local Creation Fallback
            var parentUser = db.userDao().getUserByEmail(normalizedParentEmail)
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

    suspend fun createChildProfileAndCode(childName: String): Result<Pair<UserEntity, String>> = withContext(Dispatchers.IO) {
        try {
            val activeParent = _currentUser.value ?: return@withContext Result.failure(Exception("Active parent session required to add child."))
            val cleanName = childName.trim()
            if (cleanName.isBlank()) {
                return@withContext Result.failure(Exception("Child name cannot be empty."))
            }

            // 1. Server request
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.createChildProfile(
                    com.example.data.remote.CreateChildRequestDto(
                        parent_id = activeParent.userId,
                        group_id = activeParent.groupId,
                        child_name = cleanName
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val childUser = UserEntity(
                        userId = body.child_user.user_id,
                        groupId = body.child_user.group_id,
                        email = body.child_user.email,
                        password = "",
                        role = "child",
                        name = body.child_user.name.ifBlank { cleanName },
                        pin = "",
                        avatar = body.child_user.avatar
                    )
                    db.userDao().insert(childUser)
                    if (_selectedChildId.value.isBlank()) {
                        _selectedChildId.value = childUser.userId
                    }
                    return@withContext Result.success(Pair(childUser, body.pairing_code))
                }
            } catch (e: Exception) {
                Log.w("FocusSenseRepo", "Server pairing code creation fallback: ${e.message}")
            }

            // 2. Local Fallback
            val childId = "child-${UUID.randomUUID().toString().take(8)}"
            val childUser = UserEntity(
                userId = childId,
                groupId = activeParent.groupId,
                email = "${cleanName.lowercase().replace(" ", "")}@family.focussense",
                password = "",
                role = "child",
                name = cleanName,
                pin = ""
            )
            db.userDao().insert(childUser)
            if (_selectedChildId.value.isBlank()) {
                _selectedChildId.value = childUser.userId
            }
            val localCode = String.format("%06d", (100000..999999).random())
            Result.success(Pair(childUser, localCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairChildDeviceWithCode(
        pairingCode: String,
        deviceName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanCode = pairingCode.trim().replace(" ", "")
            if (cleanCode.length != 6) {
                return@withContext Result.failure(Exception("Pairing code must be exactly 6 digits."))
            }

            // 1. Attempt Server Code Pairing
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.pairChildWithCode(
                    com.example.data.remote.PairChildCodeDto(
                        pairing_code = cleanCode,
                        device_name = if (deviceName.isNotBlank()) deviceName.trim() else "Child Phone"
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val group = FamilyGroupEntity(
                        groupId = body.group_id,
                        familyName = "Family Group"
                    )
                    db.familyGroupDao().insert(group)

                    val childUser = UserEntity(
                        userId = body.child_user.user_id,
                        groupId = body.group_id,
                        email = body.child_user.email,
                        password = "",
                        role = "child",
                        name = body.child_user.name,
                        pin = "",
                        avatar = body.child_user.avatar
                    )
                    db.userDao().insert(childUser)

                    val childDevice = DeviceEntity(
                        deviceId = body.device_id,
                        userId = childUser.userId,
                        deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childUser.name}'s Phone",
                        batteryPercent = 95,
                        isOnline = true
                    )
                    db.deviceDao().insert(childDevice)

                    prefs.edit().putString("saved_user_id", childUser.userId).apply()
                    _currentUser.value = childUser
                    _selectedChildId.value = childUser.userId
                    return@withContext Result.success(childUser)
                } else if (response.code() == 400 || response.code() == 404) {
                    return@withContext Result.failure(Exception("Invalid or expired 6-digit pairing code. Please generate a new code on the Parent device."))
                }
            } catch (e: Exception) {
                Log.w("FocusSenseRepo", "Server code pairing error: ${e.message}")
            }

            // 2. Local Fallback Check
            val allChildren = db.userDao().getUsersByRoleSync("child")
            val matchedChild = allChildren.firstOrNull()
            if (matchedChild != null) {
                val childDevice = DeviceEntity(
                    deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                    userId = matchedChild.userId,
                    deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${matchedChild.name}'s Phone",
                    batteryPercent = 95,
                    isOnline = true
                )
                db.deviceDao().insert(childDevice)

                prefs.edit().putString("saved_user_id", matchedChild.userId).apply()
                _currentUser.value = matchedChild
                _selectedChildId.value = matchedChild.userId
                return@withContext Result.success(matchedChild)
            }

            Result.failure(Exception("Unable to pair device with code '$cleanCode'. Please verify the code or check network connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
