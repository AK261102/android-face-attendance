package com.sb.attendance.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

fun formatCoordinate(value: Double?): String =
    value?.let { String.format(Locale.US, "%.6f", it) } ?: "—"
