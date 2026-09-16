package com.example.focussense.ui.navigation_bar

import kotlinx.serialization.Serializable

@Serializable
sealed class NavBarRoutes
{
    @Serializable
    object Home: NavBarRoutes()

    @Serializable
    object Short: NavBarRoutes()

    @Serializable
    object Pomodoro: NavBarRoutes()

    @Serializable
    object Digital_Health: NavBarRoutes()


}