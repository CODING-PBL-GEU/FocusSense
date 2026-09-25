package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: FamilyGroupEntity)

    @Query("SELECT * FROM family_groups LIMIT 1")
    fun getFamilyGroup(): Flow<FamilyGroupEntity?>
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = :role ORDER BY name ASC")
    fun getUsersByRole(role: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE userId = :id LIMIT 1")
    fun getUserById(id: String): Flow<UserEntity?>

    @Query("UPDATE users SET pin = :pin WHERE userId = :userId")
    suspend fun updatePin(userId: String, pin: String)
}

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>)

    @Query("SELECT * FROM devices WHERE userId = :userId")
    fun getDevicesForUser(userId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<DeviceEntity>>
}

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ActivityLogEntity>)

    @Query("SELECT * FROM activity_logs ORDER BY recordedAt DESC")
    fun getAllLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE childId = :childId ORDER BY recordedAt DESC")
    fun getLogsForChild(childId: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE isFlagged = 1 ORDER BY recordedAt DESC")
    fun getFlaggedLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE childId = :childId AND isFlagged = 1 ORDER BY recordedAt DESC")
    fun getFlaggedLogsForChild(childId: String): Flow<List<ActivityLogEntity>>

    @Query("UPDATE activity_logs SET isAcknowledged = 1 WHERE logId = :logId")
    suspend fun markAcknowledged(logId: String)

    @Query("DELETE FROM activity_logs WHERE logId = :logId")
    suspend fun deleteLog(logId: String)

    @Query("UPDATE activity_logs SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllSynced()

    @Query("SELECT COUNT(*) FROM activity_logs WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface ScheduleRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ScheduleRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ScheduleRuleEntity>)

    @Update
    suspend fun update(rule: ScheduleRuleEntity)

    @Query("DELETE FROM schedule_rules WHERE ruleId = :ruleId")
    suspend fun delete(ruleId: String)

    @Query("SELECT * FROM schedule_rules ORDER BY startTime ASC")
    fun getAllRules(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE childId = :childId ORDER BY startTime ASC")
    fun getRulesForChild(childId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE childId = :childId")
    suspend fun getRulesForChildSync(childId: String): List<ScheduleRuleEntity>

    @Query("UPDATE schedule_rules SET isActive = :isActive WHERE ruleId = :ruleId")
    suspend fun toggleRuleActive(ruleId: String, isActive: Boolean)
}

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: LocationPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_history WHERE childId = :childId ORDER BY recordedAt DESC LIMIT 1")
    fun getLatestLocationForChild(childId: String): Flow<LocationPointEntity?>

    @Query("SELECT * FROM location_history WHERE childId = :childId ORDER BY recordedAt DESC LIMIT 50")
    fun getLocationHistoryForChild(childId: String): Flow<List<LocationPointEntity>>

    @Query("UPDATE location_history SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllSynced()

    @Query("SELECT COUNT(*) FROM location_history WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}
