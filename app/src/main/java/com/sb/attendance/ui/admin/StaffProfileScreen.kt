package com.sb.attendance.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sb.attendance.data.db.AttendanceEntity
import com.sb.attendance.ui.appViewModel
import com.sb.attendance.ui.common.EmptyState
import com.sb.attendance.ui.common.InitialsAvatar
import com.sb.attendance.ui.common.LabelledValue
import com.sb.attendance.util.formatCoordinate
import com.sb.attendance.util.formatDate
import com.sb.attendance.util.formatTime
import java.io.File

/**
 * Admin view of one staff member: their details, enrolment state, and every attendance
 * record with selfie, date, time and coordinates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffProfileScreen(
    staffRowId: Long,
    onBack: () -> Unit,
    onEnrolFace: (Long, String) -> Unit
) {
    val viewModel = appViewModel(key = "profile-$staffRowId") {
        StaffProfileViewModel(it.repository, staffRowId)
    }
    val state by viewModel.state.collectAsState()
    val staff = state.staff

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(staff?.name ?: "Staff") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (staff == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (staff.enrolmentPhotoPath != null) {
                                AsyncImage(
                                    model = File(staff.enrolmentPhotoPath),
                                    contentDescription = "Enrolled face",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                                )
                            } else {
                                InitialsAvatar(staff.name, size = 64)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    staff.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Employee ID ${staff.employeeId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (staff.isEnrolled) "Face enrolled" else "Face not enrolled",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (staff.isEnrolled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { onEnrolFace(staff.id, staff.name) },
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (staff.isEnrolled) "Re-enrol face" else "Enrol face")
                        }
                    }
                }
            }

            item {
                Text(
                    "Attendance history (${state.records.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.records.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.EventBusy,
                        title = "No attendance yet",
                        subtitle = if (staff.isEnrolled) {
                            "Records appear here once this staff member marks attendance."
                        } else {
                            "Enrol this staff member's face so they can mark attendance."
                        }
                    )
                }
            } else {
                items(state.records, key = { it.id }) { AttendanceCard(it) }
            }
        }
    }
}

@Composable
private fun AttendanceCard(record: AttendanceEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(
                model = File(record.selfiePath),
                contentDescription = "Attendance selfie",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    LabelledValue("Date", formatDate(record.timestamp), Modifier.weight(1f))
                    LabelledValue("Time", formatTime(record.timestamp), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    LabelledValue("Latitude", formatCoordinate(record.latitude), Modifier.weight(1f))
                    LabelledValue("Longitude", formatCoordinate(record.longitude), Modifier.weight(1f))
                }
                Text(
                    "Face match ${"%.1f".format(record.matchScore * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
