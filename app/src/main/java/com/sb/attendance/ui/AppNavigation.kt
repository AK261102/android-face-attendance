package com.sb.attendance.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sb.attendance.ui.admin.AddStaffScreen
import com.sb.attendance.ui.admin.FaceEnrolmentScreen
import com.sb.attendance.ui.admin.StaffListScreen
import com.sb.attendance.ui.admin.StaffProfileScreen
import com.sb.attendance.ui.login.LoginScreen
import com.sb.attendance.ui.login.Session
import com.sb.attendance.ui.staff.MarkAttendanceScreen

private object Routes {
    const val LOGIN = "login"
    const val STAFF_LIST = "staff_list"
    const val ADD_STAFF = "add_staff"
    const val STAFF_PROFILE = "staff_profile/{staffId}"
    const val ENROL = "enrol/{staffId}/{staffName}"
    const val MARK_ATTENDANCE = "mark_attendance"

    fun staffProfile(id: Long) = "staff_profile/$id"
    fun enrol(id: Long, name: String) = "enrol/$id/${Uri.encode(name)}"
}

/**
 * The whole app is six screens. Which graph you land in is decided by the session
 * established at login: admin manages staff, staff only marks attendance.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Session is plain in-memory state: signing out or restarting returns to Login.
    var session by remember { mutableStateOf<Session?>(null) }

    val logout: () -> Unit = {
        session = null
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = { newSession ->
                session = newSession
                val destination = when (newSession) {
                    is Session.Admin -> Routes.STAFF_LIST
                    is Session.Staff -> Routes.MARK_ATTENDANCE
                }
                navController.navigate(destination) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.STAFF_LIST) {
            StaffListScreen(
                onAddStaff = { navController.navigate(Routes.ADD_STAFF) },
                onOpenStaff = { navController.navigate(Routes.staffProfile(it)) },
                onLogout = logout
            )
        }

        composable(Routes.ADD_STAFF) {
            AddStaffScreen(
                onBack = { navController.popBackStack() },
                onStaffCreated = { staffRowId ->
                    // Straight into the profile, which is where enrolment is offered.
                    navController.navigate(Routes.staffProfile(staffRowId)) {
                        popUpTo(Routes.ADD_STAFF) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Routes.STAFF_PROFILE,
            arguments = listOf(navArgument("staffId") { type = NavType.LongType })
        ) { entry ->
            val staffId = entry.arguments?.getLong("staffId") ?: return@composable
            StaffProfileScreen(
                staffRowId = staffId,
                onBack = { navController.popBackStack() },
                onEnrolFace = { id, name -> navController.navigate(Routes.enrol(id, name)) }
            )
        }

        composable(
            Routes.ENROL,
            arguments = listOf(
                navArgument("staffId") { type = NavType.LongType },
                navArgument("staffName") { type = NavType.StringType }
            )
        ) { entry ->
            val staffId = entry.arguments?.getLong("staffId") ?: return@composable
            val staffName = entry.arguments?.getString("staffName").orEmpty()
            FaceEnrolmentScreen(
                staffRowId = staffId,
                staffName = staffName,
                onFinished = { navController.popBackStack() }
            )
        }

        composable(Routes.MARK_ATTENDANCE) {
            // After process death the nav back stack is restored but `session` is not, so
            // fall back to Login rather than rendering a blank screen.
            when (val staffSession = session) {
                is Session.Staff -> MarkAttendanceScreen(session = staffSession, onLogout = logout)
                else -> LaunchedEffect(Unit) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }
    }
}
