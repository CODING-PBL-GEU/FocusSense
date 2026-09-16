package com.example.focussense.ui.navigation_bar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.navigation.NavHostController

import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute

@Composable
fun MyNavBar(navController: NavHostController)
{
    val navItems= listOf(
        NavItem("Home",Icons.Default.Home, NavBarRoutes.Home),
        NavItem("Short",Icons.Default.Shield, NavBarRoutes.Short),
        NavItem("Pomodoro",Icons.Default.Timer, NavBarRoutes.Pomodoro),
        NavItem("Digital_Health", Icons.Default.SelfImprovement, NavBarRoutes.Digital_Health)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        navItems.forEach { item->
            val isSelected = currentDestination?.hasRoute(item.route::class) == true
            NavigationBarItem(
                selected=isSelected,
                onClick={
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon={
                    Icon(
                        imageVector=item.icon,
                        contentDescription=item.title
                    )
                },
                label={ Text(item.title)}
            )
        }

    }
}

data class NavItem(
    val title:String,
    val icon: ImageVector,
    val route : NavBarRoutes
)