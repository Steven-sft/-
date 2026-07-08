package com.drivingrecorder.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.data.export.CsvExporter
import com.drivingrecorder.data.export.JsonExporter
import com.drivingrecorder.domain.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExportViewModel : ViewModel() {

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip.asStateFlow()

    private val csvExporter = CsvExporter()
    private val jsonExporter = JsonExporter()

    fun loadTrip(app: DrivingRecorderApp, tripId: Long) {
        viewModelScope.launch {
            _trip.value = app.tripRepository.getTrip(tripId)
        }
    }

    fun export(
        app: DrivingRecorderApp,
        tripId: Long,
        format: String,
        onComplete: (List<File>) -> Unit
    ) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                when (format) {
                    "csv" -> csvExporter.export(app, tripId)
                    "json" -> jsonExporter.export(app, tripId)
                    else -> emptyList()
                }
            }
            onComplete(files)
        }
    }
}
