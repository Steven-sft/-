package com.drivingrecorder.data.repository

import com.drivingrecorder.data.local.AppDatabase
import com.drivingrecorder.data.model.DataPointEntity
import com.drivingrecorder.data.model.DrivingEventEntity
import com.drivingrecorder.data.model.TripEntity
import com.drivingrecorder.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TripRepository(private val db: AppDatabase) {

    private val tripDao = db.tripDao()
    private val pointDao = db.dataPointDao()
    private val eventDao = db.drivingEventDao()

    // ===== Trip CRUD =====

    suspend fun createTrip(): Trip {
        val entity = TripEntity(startTime = System.currentTimeMillis())
        val id = tripDao.insert(entity)
        return entity.toDomain(id)
    }

    suspend fun finalizeTrip(
        tripId: Long,
        endTime: Long = System.currentTimeMillis()
    ) {
        val points = pointDao.getByTripId(tripId)
        val events = eventDao.getByTripId(tripId)

        if (points.isEmpty()) {
            tripDao.finalize(tripId, endTime, 0f, 0f, 0f, 0, 0, events.size)
            return
        }

        val maxSpeed = points.maxOf { it.speed }
        val avgSpeed = points.map { it.speed }.average().toFloat()
        val distance = calculateTotalDistance(points)
        val duration = points.last().timestamp - points.first().timestamp

        tripDao.finalize(
            id = tripId,
            endTime = endTime,
            maxSpeed = maxSpeed,
            avgSpeed = avgSpeed,
            totalDistance = distance,
            durationMs = duration,
            pointCount = points.size,
            eventCount = events.size
        )
    }

    fun getAllTripsFlow(): Flow<List<Trip>> =
        tripDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getAllTrips(): List<Trip> =
        tripDao.getAll().map { it.toDomain() }

    suspend fun getTrip(id: Long): Trip? =
        tripDao.getById(id)?.toDomain()

    suspend fun deleteTrip(id: Long) {
        pointDao.deleteByTripId(id)
        eventDao.deleteByTripId(id)
        tripDao.deleteById(id)
    }

    // ===== Data Points =====

    suspend fun insertDataPoint(point: DataPointEntity): Long =
        pointDao.insert(point)

    suspend fun insertDataPoints(points: List<DataPointEntity>): List<Long> =
        pointDao.insertAll(points)

    suspend fun getDataPoints(tripId: Long): List<DataPoint> =
        pointDao.getByTripId(tripId).map { it.toDomain() }

    suspend fun getDataPointCount(tripId: Long): Int =
        pointDao.countByTripId(tripId)

    // ===== Events =====

    suspend fun insertEvent(event: DrivingEventEntity): Long =
        eventDao.insert(event)

    suspend fun getEvents(tripId: Long): List<DrivingEvent> =
        eventDao.getByTripId(tripId).map { it.toDomain() }

    suspend fun getEventCount(tripId: Long): Int =
        eventDao.countByTripId(tripId)

    // ===== Utilities =====

    private fun calculateTotalDistance(points: List<DataPointEntity>): Float {
        if (points.size < 2) return 0f
        var distance = 0f
        for (i in 1 until points.size) {
            distance += haversineDistance(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return distance
    }

    companion object {
        private const val EARTH_RADIUS = 6371000.0 // meters

        fun haversineDistance(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Float {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return (EARTH_RADIUS * c).toFloat()
        }
    }
}

// ===== Mapping Extensions =====

private fun TripEntity.toDomain(id: Long = this.id): Trip = Trip(
    id = id,
    startTime = startTime,
    endTime = endTime,
    maxSpeed = maxSpeed,
    avgSpeed = avgSpeed,
    totalDistance = totalDistance,
    durationMs = durationMs,
    pointCount = pointCount,
    eventCount = eventCount
)

private fun DataPointEntity.toDomain(): DataPoint = DataPoint(
    id = id,
    tripId = tripId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    speed = speed,
    heading = heading,
    accuracy = accuracy,
    altitude = altitude,
    lateralAccel = lateralAccel,
    longitudinalAccel = longitudinalAccel
)

private fun DrivingEventEntity.toDomain(): DrivingEvent = DrivingEvent(
    id = id,
    tripId = tripId,
    timestamp = timestamp,
    eventType = EventType.fromName(eventType),
    latitude = latitude,
    longitude = longitude,
    speed = speed,
    heading = heading,
    severity = severity,
    description = description
)
