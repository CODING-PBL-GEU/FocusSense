package com.example.focussense.ui.navigation_bar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController


@Composable
fun ShortBlocker(navController: NavHostController) {
    Scaffold(
        bottomBar={MyNavBar(navController) }
    )
    { innerPadding ->
        Column(
            modifier= Modifier
                .fillMaxSize().padding(innerPadding),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        )
        {
            Text(
                text="Short Blocker",
                fontSize=30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text="Coming Soon",
                fontSize=20.sp,
                fontWeight = FontWeight.Bold
            )

        }


    }

}
