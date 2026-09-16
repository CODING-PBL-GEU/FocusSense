package com.example.focussense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.focussense.ui.navigation_bar.NavBarNavigation
import com.example.focussense.ui.onboarding.BlockAccessibilityService
import com.example.focussense.ui.onboarding.PermissionOnboardingScreen
import com.example.focussense.ui.theme.FocusSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusSenseTheme {
                // State to keep track if user granted everything
                var setupCompleted by remember { mutableStateOf(false) }

                if (!setupCompleted) {
                    PermissionOnboardingScreen(
                        accessibilityServiceClass = BlockAccessibilityService::class.java,
                        onAllPermissionsGranted = { setupCompleted = true }
                    )
                } else {
                    // Once everything is active, load your app's core navigation setup smoothly!
                    NavBarNavigation()
                }
            }
        }
    }
}