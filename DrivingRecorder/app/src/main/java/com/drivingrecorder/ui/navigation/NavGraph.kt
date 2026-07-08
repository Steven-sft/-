package com.drivingrecorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.drivingrecorder.ui.detail.TripDetailScreen
import com.drivingrecorder.ui.export.ExportScreen
import com.drivingrecorder.ui.history.HistoryScreen
import com.drivingrecorder.ui.home.MainMapScreen
import com.drivingrecorder.ui.recording.RecordingMapScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainMapScreen(navController = navController)
        }

        composable("history") {
            HistoryScreen(navController = navController)
        }

        composable(
            route = "recording/{tripId}",
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            RecordingMapScreen(
                navController = navController,
                tripId = tripId,
                onStop = { }
            )
        }

        composable(
            route = "detail/{tripId}",
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            TripDetailScreen(
                navController = navController,
                tripId = tripId
            )
        }

        composable(
            route = "export/{tripId}",
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            ExportScreen(
                navController = navController,
                tripId = tripId
            )
        }
    }
}
