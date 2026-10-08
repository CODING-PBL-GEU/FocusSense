package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.repository.FocusSenseRepository

class FocusSenseApplication : Application() {

    lateinit var repository: FocusSenseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = FocusSenseRepository(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Sentinel Alerts Channel
            val alertChannel = NotificationChannel(
                CHAN
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION,
                "FocusSense Safety Beacon",
                NotificationManager.IMPORTANCE_LOW
            
    }

    companion object {
        const val CHANNEL_ALERTS = "focussense_alerts"
        const val CHANNEL_LOCATION = "focussense_location"

        lateinit var instance: FocusSenseApplication
            private set
    }
}
