package com.example.focussense.ui.navigation_bar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Preview(showBackground = true)
@Composable
fun NavBarNavigation()
{
    val navController = rememberNavController();

    NavHost(
        navController= navController,
        startDestination = NavBarRoutes.Home
    ){
        composable<NavBarRoutes.Home>{
            HomeScreen(navController)
        }
        composable<NavBarRoutes.Short>
        {
            ShortBlocker(navController)
        }

        composable<NavBarRoutes.Pomodoro>
        {
            PomodoroTimer(navController)
        }

        composable<NavBarRoutes.Digital_Health>
        {
            DigitalHealth(navController)
        }
    }
}
