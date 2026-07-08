package com.drivingrecorder

import android.app.Application
import com.drivingrecorder.data.local.AppDatabase
import com.drivingrecorder.data.repository.RecordingRepository
import com.drivingrecorder.data.repository.TripRepository

/**
 * Application 类
 * 初始化全局单例：数据库、仓库
 */
class DrivingRecorderApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var tripRepository: TripRepository
        private set

    val recordingRepository = RecordingRepository()

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        tripRepository = TripRepository(database)
    }

    companion object {
        lateinit var instance: DrivingRecorderApp
            private set
    }
}
