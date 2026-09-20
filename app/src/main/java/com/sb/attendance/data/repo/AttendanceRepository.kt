package com.sb.attendance.data.repo

import android.graphics.Bitmap
import com.sb.attendance.data.SelfieStorage
import com.sb.attendance.data.db.AttendanceDao
import com.sb.attendance.data.db.AttendanceEntity
import com.sb.attendance.data.db.StaffDao
import com.sb.attendance.data.db.StaffEntity
import com.sb.attendance.face.FaceMatcher
import com.sb.attendance.face.FaceRecognitionService
import com.sb.attendance.face.VerifyResult
import com.sb.attendance.location.LocationProvider
import kotlinx.coroutines.flow.Flow

sealed interface AddStaffResult {
    data class Success(val staffRowId: Long) : AddStaffResult
    data object DuplicateEmployeeId : AddStaffResult
}

/** What the Mark Attendance screen reports back to the user. */
sealed interface MarkAttendanceResult {
    data class Recorded(val record: AttendanceEntity, val similarity: Float) : MarkAttendanceResult
    data class FaceMismatch(val similarity: Float) : MarkAttendanceResult
    data class NoFaceDetected(val message: String) : MarkAttendanceResult
    data object NotEnrolled : MarkAttendanceResult
}

/**
 * Single entry point for the UI. Holds the rule that matters most in this app:
 * an attendance row is only ever inserted after face verification succeeds.
 */
class AttendanceRepository(
    private val staffDao: StaffDao,
    private val attendanceDao: AttendanceDao,
    private val storage: SelfieStorage,
    private val recognition: FaceRecognitionService,
    private val location: LocationProvider
) {

    fun observeStaff(): Flow<List<StaffEntity>> = staffDao.observeAll()

    fun observeStaffById(id: Long): Flow<StaffEntity?> = staffDao.observeById(id)

    fun observeAttendance(staffRowId: Long): Flow<List<AttendanceEntity>> =
        attendanceDao.observeForStaff(staffRowId)

    suspend fun getStaffByEmployeeId(employeeId: String): StaffEntity? =
        staffDao.getByEmployeeId(employeeId.trim())

    suspend fun addStaff(name: String, employeeId: String): AddStaffResult {
        val trimmedId = employeeId.trim()
        if (staffDao.getByEmployeeId(trimmedId) != null) return AddStaffResult.DuplicateEmployeeId
        val rowId = staffDao.insert(StaffEntity(name = name.trim(), employeeId = trimmedId))
        return AddStaffResult.Success(rowId)
    }

    /** Stores the averaged template plus a reference photo for the staff profile. */
    suspend fun saveEnrolment(staffRowId: Long, samples: List<FloatArray>, referenceShot: Bitmap) {
        val template = FaceMatcher.averageTemplate(samples)
        val photoPath = storage.save(referenceShot, "enrol_$staffRowId")
        staffDao.setEnrolment(staffRowId, FaceMatcher.toBytes(template), photoPath)
    }

    /**
     * Verifies [selfie] against the staff member's enrolled face and, only on a match,
     * writes the attendance row with location and timestamp.
     */
    suspend fun markAttendance(staffRowId: Long, selfie: Bitmap): MarkAttendanceResult {
        val staff = staffDao.getById(staffRowId) ?: return MarkAttendanceResult.NotEnrolled
        val enrolled = staff.faceEmbedding ?: return MarkAttendanceResult.NotEnrolled

        return when (val result = recognition.verify(selfie, FaceMatcher.fromBytes(enrolled))) {
            is VerifyResult.NoFace ->
                MarkAttendanceResult.NoFaceDetected(result.reason.name)

            is VerifyResult.NotMatched ->
                // Deliberately nothing is written here.
                MarkAttendanceResult.FaceMismatch(result.similarity)

            is VerifyResult.Matched -> {
                val coordinates = location.current()
                val path = storage.save(selfie, "attendance_${staff.employeeId}")
                val record = AttendanceEntity(
                    staffRowId = staff.id,
                    employeeId = staff.employeeId,
                    timestamp = System.currentTimeMillis(),
                    selfiePath = path,
                    latitude = coordinates?.latitude,
                    longitude = coordinates?.longitude,
                    matchScore = result.similarity
                )
                val id = attendanceDao.insert(record)
                MarkAttendanceResult.Recorded(record.copy(id = id), result.similarity)
            }
        }
    }
}
