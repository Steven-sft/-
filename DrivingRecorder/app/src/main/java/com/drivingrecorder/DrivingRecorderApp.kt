package com.drivingrecorder

import android.app.Application
import com.drivingrecorder.data.local.AppDatabase
import com.drivingrecorder.data.repository.RecordingRepository
import com.drivingrecorder.data.repository.TripRepository
import com.drivingrecorder.service.RoadLocationIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DrivingRecorderApp : Application() {

    lateinit var database: AppDatabase; private set
    lateinit var tripRepository: TripRepository; private set
    val recordingRepository = RecordingRepository()
    val roadIndex = RoadLocationIndex(this)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        tripRepository = TripRepository(database)

        // 后台预加载道路里程索引
        appScope.launch {
            roadIndex.initialize()
        }
    }

    companion object {
        lateinit var instance: DrivingRecorderApp; private set
    }
}
