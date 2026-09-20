package com.sb.attendance.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * Reads a single fresh fix when attendance is marked. Returns null rather than throwing if the
 * permission is missing or no fix is available, so attendance is never blocked by GPS —
 * the record is simply stored without coordinates and the UI says so.
 */
class LocationProvider(private val context: Context) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Coordinates? {
        if (!hasPermission()) return null
        return runCatching {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30_000)
                .build()
            client.getCurrentLocation(request, null).await()
                ?.let { Coordinates(it.latitude, it.longitude) }
                ?: client.lastLocation.await()?.let { Coordinates(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
