package com.sb.attendance.ui.staff

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.attendance.data.db.AttendanceEntity
import com.sb.attendance.data.repo.AttendanceRepository
import com.sb.attendance.data.repo.MarkAttendanceResult
import com.sb.attendance.ui.admin.toUserMessage
import com.sb.attendance.face.FaceFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the staff member sees after a verification attempt. */
sealed interface AttendanceOutcome {
    data class Success(val record: AttendanceEntity, val similarity: Float) : AttendanceOutcome
    data class Rejected(val reason: String, val similarity: Float?) : AttendanceOutcome
}

data class MarkAttendanceUiState(
    val cameraOpen: Boolean = false,
    val busy: Boolean = false,
    val outcome: AttendanceOutcome? = null,
    val enrolled: Boolean = true
)

class MarkAttendanceViewModel(
    private val repository: AttendanceRepository,
    private val staffRowId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(MarkAttendanceUiState())
    val state: StateFlow<MarkAttendanceUiState> = _state.asStateFlow()

    fun openCamera() {
        _state.value = _state.value.copy(cameraOpen = true, outcome = null)
    }

    fun closeCamera() {
        _state.value = _state.value.copy(cameraOpen = false)
    }

    fun reset() {
        _state.value = MarkAttendanceUiState()
    }

    /**
     * Hands the selfie to the repository, which records attendance only if the face matches.
     * Every branch here is a report of what the repository decided — the UI never writes.
     */
    fun onSelfieCaptured(selfie: Bitmap) {
        if (_state.value.busy) return

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, cameraOpen = false)

            val outcome = when (val result = repository.markAttendance(staffRowId, selfie)) {
                is MarkAttendanceResult.Recorded ->
                    AttendanceOutcome.Success(result.record, result.similarity)

                is MarkAttendanceResult.FaceMismatch -> AttendanceOutcome.Rejected(
                    "Face did not match the enrolled staff member. Attendance was not recorded.",
                    result.similarity
                )

                is MarkAttendanceResult.NoFaceDetected -> AttendanceOutcome.Rejected(
                    runCatching { FaceFailure.valueOf(result.message).toUserMessage() }
                        .getOrDefault("No face detected. Attendance was not recorded."),
                    null
                )

                MarkAttendanceResult.NotEnrolled -> AttendanceOutcome.Rejected(
                    "Your face has not been enrolled yet. Ask the admin to enrol you.",
                    null
                )
            }

            selfie.recycle()
            _state.value = _state.value.copy(busy = false, outcome = outcome)
        }
    }
}
