package com.sb.attendance.ui.staff

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sb.attendance.ui.appViewModel
import com.sb.attendance.ui.common.BusyOverlay
import com.sb.attendance.ui.common.CameraController
import com.sb.attendance.ui.common.FrontCameraPreview
import com.sb.attendance.ui.common.RequirePermissions
import com.sb.attendance.ui.common.ValueRow
import com.sb.attendance.ui.login.Session
import com.sb.attendance.util.formatCoordinate
import com.sb.attendance.util.formatDate
import com.sb.attendance.util.formatTime
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkAttendanceScreen(session: Session.Staff, onLogout: () -> Unit) {
    val viewModel = appViewModel(key = "attendance-${session.staffRowId}") {
        MarkAttendanceViewModel(it.repository, session.staffRowId)
    }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.name) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.cameraOpen -> SelfieCapture(
                    onCaptured = viewModel::onSelfieCaptured,
                    onCancel = viewModel::closeCamera
                )

                else -> Idle(
                    employeeId = session.employeeId,
                    outcome = state.outcome,
                    onMark = viewModel::openCamera,
                    onClear = viewModel::reset
                )
            }

            if (state.busy) BusyOverlay("Verifying your face…")
        }
    }
}

@Composable
private fun Idle(
    employeeId: String,
    outcome: AttendanceOutcome?,
    onMark: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Employee ID $employeeId",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onMark,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Mark attendance", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            "A selfie is taken with the front camera and checked against your enrolled face. " +
                "Attendance is recorded only if it matches.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )

        outcome?.let {
            Spacer(Modifier.height(24.dp))
            OutcomeCard(it)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}

/** Shows the full stored record on success, and an unmistakable rejection otherwise. */
@Composable
private fun OutcomeCard(outcome: AttendanceOutcome) {
    when (outcome) {
        is AttendanceOutcome.Success -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Attendance recorded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(12.dp))

                AsyncImage(
                    model = File(outcome.record.selfiePath),
                    contentDescription = "Attendance selfie",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(12.dp))

                ValueRow(
                    "Date", formatDate(outcome.record.timestamp),
                    "Time", formatTime(outcome.record.timestamp)
                )
                Spacer(Modifier.height(8.dp))
                ValueRow(
                    "Latitude", formatCoordinate(outcome.record.latitude),
                    "Longitude", formatCoordinate(outcome.record.longitude)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Face match ${"%.1f".format(outcome.similarity * 100)}%",
                    style = MaterialTheme.typography.labelMedium
                )
                if (outcome.record.latitude == null) {
                    Text(
                        "Location unavailable — the record was saved without coordinates.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        is AttendanceOutcome.Rejected -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Not recorded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(outcome.reason, style = MaterialTheme.typography.bodyMedium)
                outcome.similarity?.let {
                    Text(
                        "Similarity ${"%.1f".format(it * 100)}% — below the required threshold.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelfieCapture(
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    RequirePermissions(
        // Location is requested here too, so a successful mark already has a fix to attach.
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
        required = listOf(Manifest.permission.CAMERA),
        rationale = "Camera access is needed to take the attendance selfie."
    ) {
        val scope = rememberCoroutineScope()
        var controller by remember { mutableStateOf<CameraController?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        Box(Modifier.fillMaxSize()) {
            FrontCameraPreview(Modifier.fillMaxSize()) { controller = it }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }

            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            it,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    enabled = controller != null,
                    onClick = {
                        val camera = controller ?: return@Button
                        scope.launch {
                            error = null
                            runCatching { camera.capture() }
                                .onSuccess(onCaptured)
                                .onFailure { error = "Capture failed: ${it.message}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(if (controller == null) "Starting camera…" else "Take selfie")
                }
            }
        }
    }
}
