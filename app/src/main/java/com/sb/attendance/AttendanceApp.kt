package com.sb.attendance

import android.app.Application
import com.sb.attendance.data.SelfieStorage
import com.sb.attendance.data.db.AppDatabase
import com.sb.attendance.data.repo.AttendanceRepository
import com.sb.attendance.face.FaceRecognitionService
import com.sb.attendance.location.LocationProvider

/**
 * Manual dependency container. The app has one repository and three collaborators, so a
 * DI framework would add build complexity without buying anything here.
 */
class AttendanceApp : Application() {

    val recognition: FaceRecognitionService by lazy { FaceRecognitionService(this) }

    val repository: AttendanceRepository by lazy {
        val db = AppDatabase.get(this)
        AttendanceRepository(
            staffDao = db.staffDao(),
            attendanceDao = db.attendanceDao(),
            storage = SelfieStorage(this),
            recognition = recognition,
            location = LocationProvider(this)
        )
    }
}
