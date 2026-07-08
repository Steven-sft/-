package com.drivingrecorder.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * GPS 定位服务封装
 * 基于 Google Play Services FusedLocationProviderClient
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    /**
     * 创建高精度位置请求
     */
    private fun createLocationRequest(): LocationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            200L  // 200ms间隔 = 5Hz高频采集
        ).apply {
            setMinUpdateIntervalMillis(100L)      // 最快0.1秒
            setMaxUpdateDelayMillis(500L)         // 最大延迟0.5秒
            setWaitForAccurateLocation(true)
        }.build()

    /**
     * 持续获取位置更新的 Flow
     */
    @SuppressLint("MissingPermission")
    fun getLocationFlow(): Flow<Location> = callbackFlow {
        val request = createLocationRequest()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // 位置服务不可用时不做特殊处理，继续等待
            }
        }

        locationCallback = callback

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * 获取单次位置（用于快速初始化）
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 移除位置更新
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    /**
     * 检查位置服务是否可用
     */
    fun isLocationAvailable(): Boolean {
        return try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    // Google Play Services Task → Kotlin coroutine 挂起
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? {
        return try {
            com.google.android.gms.tasks.Tasks.await(this)
        } catch (e: Exception) {
            null
        }
    }
}
