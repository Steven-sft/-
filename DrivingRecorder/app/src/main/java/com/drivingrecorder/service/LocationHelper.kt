package com.drivingrecorder.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var locationCallback: LocationCallback? = null

    private fun createLocationRequest(): LocationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMaxUpdateDelayMillis(2000L)
            setWaitForAccurateLocation(false)
            setDurationMillis(Long.MAX_VALUE)
        }.build()

    /**
     * 检查并提示开启高精度定位
     */
    fun checkAndPromptHighAccuracy(): Boolean {
        val gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsOn && !networkOn) {
            // 提示打开位置服务
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun getLocationFlow(): Flow<Location> = callbackFlow {
        if (!isAnyProviderEnabled()) {
            // 尝试发送最后的已知位置
            getLastLocation()?.let { trySend(it) }
        }

        val request = createLocationRequest()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    if (isValidLocation(location)) {
                        trySend(location)
                    }
                }
            }
        }

        locationCallback = callback

        // 主定位：FusedLocationProvider (GPS + 网络融合)
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * 单次定位（供定位按钮使用）
     * 超时15秒，返回最佳可用位置
     */
    @SuppressLint("MissingPermission")
    suspend fun requestSingleLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            // 先拿最后已知位置
            val last = fusedLocationClient.lastLocation.await()

            // 请求新的位置，最多等10秒
            val freshRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
                .setMaxUpdateDelayMillis(3000L)
                .setDurationMillis(10000L)
                .build()

            val fresh = withTimeoutOrNull(10000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cb = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val loc = result.lastLocation
                            if (loc != null) {
                                cont.resume(loc)
                                // 拿到一个就取消
                                fusedLocationClient.removeLocationUpdates(this)
                            }
                        }
                    }
                    cont.invokeOnCancellation { fusedLocationClient.removeLocationUpdates(cb) }
                    fusedLocationClient.requestLocationUpdates(freshRequest, cb, Looper.getMainLooper())
                }
            }

            // 返回精度最好的
            return@withContext listOfNotNull(last, fresh)
                .filter { !it.isFromMockProvider }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else 9999f }
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidLocation(location: Location): Boolean {
        if (location.isFromMockProvider) return false
        val ageMs = System.currentTimeMillis() - location.time
        if (ageMs > 120_000) return false  // 2分钟内有效

        return if (location.hasAccuracy()) {
            location.accuracy > 0 && location.accuracy <= 200f  // 放宽到200米
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()?.takeIf { isValidLocation(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    fun isGpsEnabled() = try {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: Exception) { false }

    fun isAnyProviderEnabled() = try {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) { false }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? {
        return try { com.google.android.gms.tasks.Tasks.await(this) }
        catch (e: Exception) { null }
    }
}
