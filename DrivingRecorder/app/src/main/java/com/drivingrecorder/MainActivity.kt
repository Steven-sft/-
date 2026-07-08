package com.drivingrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.drivingrecorder.ui.navigation.NavGraph
import com.drivingrecorder.ui.theme.DrivingRecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrivingRecorderTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
