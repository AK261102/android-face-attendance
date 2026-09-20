package com.sb.attendance.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.attendance.data.repo.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val error: String? = null,
    val busy: Boolean = false
)

class LoginViewModel(private val repository: AttendanceRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    /**
     * Admin matches the fixed demo account. Anything else is treated as an Employee ID and
     * looked up in the staff table, which binds the session to that one staff row.
     */
    fun submit(onSuccess: (Session) -> Unit) {
        val username = _state.value.username.trim()
        val password = _state.value.password

        if (username.isEmpty() || password.isEmpty()) {
            _state.value = _state.value.copy(error = "Enter a username and password")
            return
        }

        if (username.equals(DemoCredentials.ADMIN_USERNAME, ignoreCase = true)) {
            if (password == DemoCredentials.ADMIN_PASSWORD) {
                onSuccess(Session.Admin)
            } else {
                _state.value = _state.value.copy(error = "Incorrect admin password")
            }
            return
        }

        if (password != DemoCredentials.STAFF_PASSWORD) {
            _state.value = _state.value.copy(error = "Incorrect password")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val staff = repository.getStaffByEmployeeId(username)
            _state.value = _state.value.copy(busy = false)
            if (staff == null) {
                _state.value = _state.value.copy(
                    error = "No staff member with Employee ID \"$username\". Ask the admin to add you first."
                )
            } else {
                onSuccess(Session.Staff(staff.id, staff.name, staff.employeeId))
            }
        }
    }
}
