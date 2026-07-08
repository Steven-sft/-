package com.drivingrecorder.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel : ViewModel() {

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip.asStateFlow()

    private val _events = MutableStateFlow<List<DrivingEvent>>(emptyList())
    val events: StateFlow<List<DrivingEvent>> = _events.asStateFlow()

    fun loadTrip(app: DrivingRecorderApp, tripId: Long) {
        viewModelScope.launch {
            _trip.value = app.tripRepository.getTrip(tripId)
            _events.value = app.tripRepository.getEvents(tripId)
        }
    }

    fun deleteTrip(app: DrivingRecorderApp, tripId: Long) {
        viewModelScope.launch {
            app.tripRepository.deleteTrip(tripId)
        }
    }
}
