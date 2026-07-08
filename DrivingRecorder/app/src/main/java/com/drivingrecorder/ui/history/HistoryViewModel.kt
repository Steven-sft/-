package com.drivingrecorder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    fun loadTrips(app: DrivingRecorderApp) {
        viewModelScope.launch {
            app.tripRepository.getAllTripsFlow().collect { list ->
                _trips.value = list
            }
        }
    }
}
