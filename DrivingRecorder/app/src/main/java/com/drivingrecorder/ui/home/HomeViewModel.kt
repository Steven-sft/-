package com.drivingrecorder.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.domain.model.Trip
import com.drivingrecorder.service.RecordingForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    fun loadTrips(app: DrivingRecorderApp) {
        viewModelScope.launch {
            app.tripRepository.getAllTripsFlow().collect { list ->
                _trips.value = list
            }
        }
    }

    fun startRecording(context: Context, navController: NavController) {
        val app = context.applicationContext as DrivingRecorderApp
        viewModelScope.launch {
            val trip = app.tripRepository.createTrip()
            app.recordingRepository.startRecording(trip.id)
            RecordingForegroundService.startService(context, trip.id)
            // 跳转到地图轨迹界面
            navController.navigate("recording/${trip.id}")
        }
    }

    fun stopRecording(context: Context, navController: NavController) {
        val app = context.applicationContext as DrivingRecorderApp
        val tripId = app.recordingRepository.currentTripId.value
        RecordingForegroundService.stopService(context)
        app.recordingRepository.stopRecording()
        // 返回首页后跳转行程详情
        navController.navigate("home") {
            popUpTo("home") { inclusive = true }
        }
        tripId?.let { id ->
            navController.navigate("detail/$id")
        }
    }
}
