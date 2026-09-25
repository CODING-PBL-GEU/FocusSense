package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FamilyGroupEntity::class,
        UserEntity::class,
        DeviceEntity::class,
        ActivityLogEntity::class,
        ScheduleRuleEntity::class,
        LocationPointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FocusSenseDatabase : RoomDatabase() {

    abstract fun familyGroupDao(): FamilyGroupDao
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: FocusSenseDatabase? = null

        fun getInstance(context: Context): FocusSenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusSenseDatabase::class.java,
                    "focussense_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: FocusSenseDatabase) {
            val now = System.currentTimeMillis()

            // 1. Family Group
            val group = FamilyGroupEntity(
                groupId = "group-connor",
                familyName = "Connor Family",
                createdAt = now - 86400000L * 30
            )
            database.familyGroupDao().insert(group)

            // 2. Users
            val parent = UserEntity(
                userId = "parent-sarah",
                groupId = "group-connor",
                email = "sarah.connor@focussense.io",
                password = "demo",
                role = "parent",
                name = "Sarah Connor (Mom)",
                pin = "1234",
                avatar = "parent_avatar"
            )
            val child1 = UserEntity(
                userId = "child-leo",
                groupId = "group-connor",
                email = "leo@focussense.io",
                password = "demo",
                role = "child",
                name = "Leo (12 yrs)",
                pin = "",
                avatar = "child_leo"
            )
            val child2 = UserEntity(
                userId = "child-maya",
                groupId = "group-connor",
                email = "maya@focussense.io",
                password = "demo",
                role = "child",
                name = "Maya (15 yrs)",
                pin = "",
                avatar = "child_maya"
            )
            database.userDao().insertAll(listOf(parent, child1, child2))

            // 3. Devices
            val devLeo = DeviceEntity(
                deviceId = "dev-leo-pixel",
                userId = "child-leo",
                deviceName = "Leo's Pixel 8",
                token = "fcm_token_leo_4892",
                batteryPercent = 82,
                isOnline = true,
                lastActive = now - 60000L * 2
            )
            val devMaya = DeviceEntity(
                deviceId = "dev-maya-galaxy",
                userId = "child-maya",
                deviceName = "Maya's Galaxy S24",
                token = "fcm_token_maya_7719",
                batteryPercent = 94,
                isOnline = true,
                lastActive = now - 60000L * 15
            )
            val devSarah = DeviceEntity(
                deviceId = "dev-sarah-parent",
                userId = "parent-sarah",
                deviceName = "Sarah's iPhone Pro",
                token = "fcm_token_sarah_1102",
                batteryPercent = 71,
                isOnline = true,
                lastActive = now
            )
            database.deviceDao().insertAll(listOf(devLeo, devMaya, devSarah))

            // 4. Schedule Rules (Time table & app restriction windows)
            val rulesLeo = listOf(
                ScheduleRuleEntity(
                    ruleId = "rule-leo-homework",
                    childId = "child-leo",
                    ruleName = "Afternoon Homework & Study",
                    category = "Homework",
                    startTime = "15:30",
                    endTime = "17:30",
                    dayOfWeek = "Mon,Tue,Wed,Thu,Fri",
                    restrictedPackages = "com.google.android.youtube,com.zhiliaoapp.musically,com.roblox.client,com.discord",
                    isActive = true
                ),
                ScheduleRuleEntity(
                    ruleId = "rule-leo-bedtime",
                    childId = "child-leo",
                    ruleName = "Digital Curfew (Healthy Sleep)",
                    category = "Bedtime",
                    startTime = "21:00",
                    endTime = "06:30",
                    dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
                    restrictedPackages = "com.google.android.youtube,com.zhiliaoapp.musically,com.roblox.client,com.instagram.android,com.discord,com.netflix.mediaclient",
                    isActive = true
                ),
                ScheduleRuleEntity(
                    ruleId = "rule-leo-outdoor",
                    childId = "child-leo",
                    ruleName = "Outdoor Play & Soccer Habit",
                    category = "Outdoor",
                    startTime = "17:30",
                    endTime = "19:00",
                    dayOfWeek = "Mon,Tue,Wed,Thu,Fri",
                    restrictedPackages = "com.roblox.client,com.epicgames.fortnite",
                    isActive = true
                ),
                ScheduleRuleEntity(
                    ruleId = "rule-leo-school",
                    childId = "child-leo",
                    ruleName = "School Classroom Focus",
                    category = "Study",
                    startTime = "08:30",
                    endTime = "14:30",
                    dayOfWeek = "Mon,Tue,Wed,Thu,Fri",
                    restrictedPackages = "com.roblox.client,com.zhiliaoapp.musically,com.discord",
                    isActive = true
                )
            )

            val rulesMaya = listOf(
                ScheduleRuleEntity(
                    ruleId = "rule-maya-study",
                    childId = "child-maya",
                    ruleName = "High School Exam Focus Window",
                    category = "Study",
                    startTime = "16:00",
                    endTime = "18:30",
                    dayOfWeek = "Mon,Tue,Wed,Thu,Fri",
                    restrictedPackages = "com.instagram.android,com.zhiliaoapp.musically,com.snapchat.android",
                    isActive = true
                ),
                ScheduleRuleEntity(
                    ruleId = "rule-maya-sleep",
                    childId = "child-maya",
                    ruleName = "Night Curfew",
                    category = "Bedtime",
                    startTime = "22:30",
                    endTime = "07:00",
                    dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
                    restrictedPackages = "com.instagram.android,com.zhiliaoapp.musically,com.snapchat.android,com.netflix.mediaclient",
                    isActive = true
                )
            )
            database.scheduleRuleDao().insertAll(rulesLeo + rulesMaya)

            // 5. Activity Logs (Contextual text extracted via AccessibilityService & Evaluated with MobileBERT / Gemini)
            val logsLeo = listOf(
                ActivityLogEntity(
                    logId = "log-1",
                    childId = "child-leo",
                    packageName = "com.discord",
                    appName = "Discord",
                    contentTitle = "Direct Message - User 'ShadowGamer99'",
                    extractedText = "Hey are you alone right now? Don't tell your mom or dad, let's meet up at the skatepark behind the old mill at 6pm. I have free Robux codes for you.",
                    isFlagged = true,
                    threatCategory = "Stranger Risk",
                    confidenceScore = 0.96f,
                    aiAnalysisSummary = "Critical safety alert: Solicitation of an unsupervised meeting from an unknown contact, accompanied by explicit instructions for secrecy and gift luring.",
                    recordedAt = now - 60000L * 18,
                    isSynced = true,
                    isAcknowledged = false
                ),
                ActivityLogEntity(
                    logId = "log-2",
                    childId = "child-leo",
                    packageName = "com.android.chrome",
                    appName = "Chrome Browser",
                    contentTitle = "Google Search",
                    extractedText = "how to bypass plagiarism turnitin write my 6th grade science essay fast bot generator",
                    isFlagged = true,
                    threatCategory = "Academic Distraction",
                    confidenceScore = 0.89f,
                    aiAnalysisSummary = "Academic integrity alert: Attempt to locate automated bots and essay generation tools to circumvent school plagiarism detectors.",
                    recordedAt = now - 60000L * 45,
                    isSynced = true,
                    isAcknowledged = false
                ),
                ActivityLogEntity(
                    logId = "log-3",
                    childId = "child-leo",
                    packageName = "com.google.android.youtube",
                    appName = "YouTube",
                    contentTitle = "Khan Academy: Ecosystems and Food Webs",
                    extractedText = "Photosynthesis creates primary energy in autotrophs, transferred upwards through trophic levels in aquatic and terrestrial biomes.",
                    isFlagged = false,
                    threatCategory = "Safe",
                    confidenceScore = 0.02f,
                    aiAnalysisSummary = "Educational science material: High cognitive value and curricular relevance.",
                    recordedAt = now - 60000L * 90,
                    isSynced = true,
                    isAcknowledged = true
                ),
                ActivityLogEntity(
                    logId = "log-4",
                    childId = "child-leo",
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    contentTitle = "Group Chat 'School 7B'",
                    extractedText = "Nobody wants you sitting at our lunch table tomorrow, you are such a loser just don't even show up to class.",
                    isFlagged = true,
                    threatCategory = "Cyberbullying",
                    confidenceScore = 0.93f,
                    aiAnalysisSummary = "Harassment & ostracization detected: Hostile group targeting aiming to intimidate and emotionally distress the child.",
                    recordedAt = now - 3600000L * 4,
                    isSynced = true,
                    isAcknowledged = false
                ),
                ActivityLogEntity(
                    logId = "log-5",
                    childId = "child-leo",
                    packageName = "com.duolingo",
                    appName = "Duolingo",
                    contentTitle = "Spanish Practice Session",
                    extractedText = "Complete the sentence: ¿Dónde está la biblioteca central? Práctica de conversación diaria.",
                    isFlagged = false,
                    threatCategory = "Safe",
                    confidenceScore = 0.01f,
                    aiAnalysisSummary = "Language learning application: constructive vocabulary retention exercise.",
                    recordedAt = now - 3600000L * 6,
                    isSynced = true,
                    isAcknowledged = true
                )
            )
            database.activityLogDao().insertAll(logsLeo)

            // 6. Location History (GPS tracking points)
            val locHistory = listOf(
                LocationPointEntity(
                    locId = "loc-1",
                    childId = "child-leo",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    accuracy = 4.2f,
                    locationName = "Oakwood Middle School",
                    recordedAt = now - 60000L * 30,
                    isSynced = true
                ),
                LocationPointEntity(
                    locId = "loc-2",
                    childId = "child-leo",
                    latitude = 37.7793,
                    longitude = -122.4168,
                    accuracy = 5.0f,
                    locationName = "Civic Center Library",
                    recordedAt = now - 60000L * 15,
                    isSynced = true
                ),
                LocationPointEntity(
                    locId = "loc-3",
                    childId = "child-leo",
                    latitude = 37.7690,
                    longitude = -122.4467,
                    accuracy = 3.5f,
                    locationName = "Home (Sweet Home)",
                    recordedAt = now - 60000L * 2,
                    isSynced = true
                ),
                LocationPointEntity(
                    locId = "loc-maya-1",
                    childId = "child-maya",
                    latitude = 37.7690,
                    longitude = -122.4467,
                    accuracy = 3.8f,
                    locationName = "Home (Sweet Home)",
                    recordedAt = now - 60000L * 5,
                    isSynced = true
                )
            )
            database.locationDao().insertAll(locHistory)
        }
    }
}
