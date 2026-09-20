package com.sb.attendance.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sb.attendance.data.db.StaffEntity
import com.sb.attendance.ui.appViewModel
import com.sb.attendance.ui.common.EmptyState
import com.sb.attendance.ui.common.InitialsAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffListScreen(
    onAddStaff: () -> Unit,
    onOpenStaff: (Long) -> Unit,
    onLogout: () -> Unit
) {
    val viewModel = appViewModel { StaffListViewModel(it.repository) }
    val staff by viewModel.staff.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddStaff,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add staff") }
            )
        }
    ) { padding ->
        if (staff.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Group,
                    title = "No staff yet",
                    subtitle = "Tap \"Add staff\" to create the first staff member."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(staff, key = { it.id }) { member ->
                    StaffRow(member, onClick = { onOpenStaff(member.id) })
                }
            }
        }
    }
}

@Composable
private fun StaffRow(staff: StaffEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(staff.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    staff.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "ID ${staff.employeeId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            EnrolmentBadge(staff.isEnrolled)
        }
    }
}

/** Makes it obvious at a glance which staff members can actually mark attendance. */
@Composable
private fun EnrolmentBadge(enrolled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (enrolled) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (enrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (enrolled) "Enrolled" else "No face",
            style = MaterialTheme.typography.labelSmall,
            color = if (enrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
