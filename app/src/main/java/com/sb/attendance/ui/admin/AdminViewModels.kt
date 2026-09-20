package com.sb.attendance.ui.admin

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.attendance.data.db.AttendanceEntity
import com.sb.attendance.data.db.StaffEntity
import com.sb.attendance.data.repo.AddStaffResult
import com.sb.attendance.data.repo.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StaffListViewModel(repository: AttendanceRepository) : ViewModel() {
    val staff: StateFlow<List<StaffEntity>> = repository.observeStaff()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class AddStaffUiState(
    val name: String = "",
    val employeeId: String = "",
    val error: String? = null,
    val busy: Boolean = false
)

class AddStaffViewModel(private val repository: AttendanceRepository) : ViewModel() {

    private val _state = MutableStateFlow(AddStaffUiState())
    val state: StateFlow<AddStaffUiState> = _state.asStateFlow()

    fun onNameChange(value: String) { _state.value = _state.value.copy(name = value, error = null) }

    fun onEmployeeIdChange(value: String) {
        _state.value = _state.value.copy(employeeId = value, error = null)
    }

    /** On success the caller is handed the new row id so it can go straight to enrolment. */
    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.value = current.copy(error = "Staff name is required"); return
        }
        if (current.employeeId.isBlank()) {
            _state.value = current.copy(error = "Employee ID is required"); return
        }

        viewModelScope.launch {
            _state.value = current.copy(busy = true)
            when (val result = repository.addStaff(current.name, current.employeeId)) {
                is AddStaffResult.DuplicateEmployeeId ->
                    _state.value = current.copy(busy = false, error = "That Employee ID already exists")
                is AddStaffResult.Success -> {
                    _state.value = AddStaffUiState()
                    onSaved(result.staffRowId)
                }
            }
        }
    }
}

data class StaffProfileUiState(
    val staff: StaffEntity? = null,
    val records: List<AttendanceEntity> = emptyList()
)

class StaffProfileViewModel(
    private val repository: AttendanceRepository,
    staffRowId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(StaffProfileUiState())
    val state: StateFlow<StaffProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeStaffById(staffRowId).collect { staff ->
                _state.value = _state.value.copy(staff = staff)
            }
        }
        viewModelScope.launch {
            repository.observeAttendance(staffRowId).collect { records ->
                _state.value = _state.value.copy(records = records)
            }
        }
    }
}

/** Progress through the multi-shot enrolment flow. */
data class EnrolmentUiState(
    val samplesCaptured: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val done: Boolean = false
) {
    val totalSamples: Int get() = EnrolmentViewModel.REQUIRED_SAMPLES
}

class EnrolmentViewModel(
    private val repository: AttendanceRepository,
    private val recognition: com.sb.attendance.face.FaceRecognitionService,
    private val staffRowId: Long
) : ViewModel() {

    private val samples = mutableListOf<FloatArray>()
    private var referenceShot: Bitmap? = null

    private val _state = MutableStateFlow(EnrolmentUiState())
    val state: StateFlow<EnrolmentUiState> = _state.asStateFlow()

    /**
     * Each capture is detected, embedded and kept. Once [REQUIRED_SAMPLES] good shots are in,
     * they are averaged into the stored template. A frame with no clear face is rejected and
     * simply does not advance the counter.
     */
    fun onFrameCaptured(frame: Bitmap) {
        if (_state.value.busy || _state.value.done) return

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)

            when (val result = recognition.embed(frame)) {
                is com.sb.attendance.face.EmbedResult.Failure -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = result.reason.toUserMessage()
                    )
                    frame.recycle()
                }

                is com.sb.attendance.face.EmbedResult.Success -> {
                    samples += result.embedding
                    // Keep the first good shot as the profile photo.
                    if (referenceShot == null) referenceShot = result.crop else result.crop.recycle()

                    if (samples.size >= REQUIRED_SAMPLES) {
                        repository.saveEnrolment(staffRowId, samples, referenceShot!!)
                        _state.value = _state.value.copy(
                            busy = false,
                            samplesCaptured = samples.size,
                            done = true,
                            message = "Face enrolled"
                        )
                    } else {
                        _state.value = _state.value.copy(
                            busy = false,
                            samplesCaptured = samples.size,
                            message = "Captured ${samples.size} of $REQUIRED_SAMPLES — change your angle slightly"
                        )
                    }
                    if (frame !== referenceShot) frame.recycle()
                }
            }
        }
    }

    companion object {
        /** Averaging three shots makes the template tolerant of pose and lighting. */
        const val REQUIRED_SAMPLES = 3
    }
}

internal fun com.sb.attendance.face.FaceFailure.toUserMessage(): String = when (this) {
    com.sb.attendance.face.FaceFailure.NO_FACE -> "No face detected — hold the phone at arm's length in good light"
    com.sb.attendance.face.FaceFailure.MULTIPLE_FACES -> "More than one face in frame — make sure only you are visible"
    com.sb.attendance.face.FaceFailure.TOO_SMALL -> "Move closer to the camera"
}
