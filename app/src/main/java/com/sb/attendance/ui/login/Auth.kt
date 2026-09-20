package com.sb.attendance.ui.login

/**
 * Dummy credentials, as permitted by the assignment brief. Admin is a fixed account;
 * a staff member signs in with their Employee ID and a shared demo password, which binds
 * the session to exactly one staff row so attendance can be verified 1:1 against that
 * person's enrolled face.
 */
object DemoCredentials {
    const val ADMIN_USERNAME = "admin"
    const val ADMIN_PASSWORD = "admin123"
    const val STAFF_PASSWORD = "staff123"
}

sealed interface Session {
    data object Admin : Session
    data class Staff(val staffRowId: Long, val name: String, val employeeId: String) : Session
}
