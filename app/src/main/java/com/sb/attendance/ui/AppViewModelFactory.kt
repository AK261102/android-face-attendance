package com.sb.attendance.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sb.attendance.AttendanceApp

/**
 * Bridges the manual dependency container in [AttendanceApp] to Compose's `viewModel()`,
 * so screens can ask for a ViewModel without knowing how its dependencies are built.
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AttendanceApp) -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = checkNotNull(extras[APPLICATION_KEY]) as AttendanceApp
        @Suppress("UNCHECKED_CAST")
        return create(app) as T
    }
}

/**
 * @param key distinguishes ViewModels of the same type scoped to different arguments,
 *   e.g. one [com.sb.attendance.ui.admin.StaffProfileViewModel] per staff member.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AttendanceApp) -> VM
): VM = viewModel(key = key, factory = appViewModelFactory { create(it) })
