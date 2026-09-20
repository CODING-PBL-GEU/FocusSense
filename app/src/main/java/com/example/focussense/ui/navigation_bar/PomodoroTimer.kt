package com.example.focussense.ui.navigation_bar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

@Composable
fun PomodoroTimer(navController: NavHostController) {

    var timeLeft by remember { mutableStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var isBreak by remember { mutableStateOf(false) }
    var completedPomodoros by remember { mutableStateOf(0) }

    LaunchedEffect(isRunning) {

        while (isRunning) {

            delay(1000)

            if (timeLeft > 1) {

                timeLeft--

            } else {

                if (!isBreak) {

                    completedPomodoros++

                    isBreak = true

                    timeLeft = if (completedPomodoros % 4 == 0) {
                        15 * 60
                    } else {
                        5 * 60
                    }

                } else {

                    isBreak = false
                    timeLeft = 25 * 60
                }
            }
        }
    }

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60

    Scaffold(
        bottomBar = {
            MyNavBar(navController)
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = if (isBreak) "Break Time" else "Focus Time",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = String.format(
                            "%02d:%02d",
                            minutes,
                            seconds
                        ),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isBreak)
                            "Take a short rest"
                        else
                            "Stay focused on your task",
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    onClick = {
                        isRunning = !isRunning
                    }
                ) {
                    Text(
                        text = if (isRunning) "Pause" else "Start"
                    )
                }

                OutlinedButton(
                    onClick = {
                        isRunning = false
                        isBreak = false
                        timeLeft = 25 * 60
                    }
                ) {
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Completed Pomodoros: $completedPomodoros",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}