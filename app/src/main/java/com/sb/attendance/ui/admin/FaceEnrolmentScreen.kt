package com.sb.attendance.ui.admin

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sb.attendance.ui.appViewModel
import com.sb.attendance.ui.common.BusyOverlay
import com.sb.attendance.ui.common.CameraController
import com.sb.attendance.ui.common.FrontCameraPreview
import com.sb.attendance.ui.common.RequirePermissions
import kotlinx.coroutines.launch

/**
 * Captures several shots of the staff member's face and stores the averaged template.
 * Uses the front camera, as the brief requires.
 */
@Composable
fun FaceEnrolmentScreen(
    staffRowId: Long,
    staffName: String,
    onFinished: () -> Unit
) {
    RequirePermissions(
        permissions = listOf(Manifest.permission.CAMERA),
        required = listOf(Manifest.permission.CAMERA),
        rationale = "Camera access is needed to enrol the staff member's face."
    ) {
        EnrolmentContent(staffRowId, staffName, onFinished)
    }
}

@Composable
private fun EnrolmentContent(staffRowId: Long, staffName: String, onFinished: () -> Unit) {
    val viewModel = appViewModel(key = "enrol-$staffRowId") { app ->
        EnrolmentViewModel(app.repository, app.recognition, staffRowId)
    }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<CameraController?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        FrontCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onReady = { controller = it }
        )

        // Instructions and progress over the live preview.
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Enrolling $staffName",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Capture ${state.totalSamples} shots — look straight at the camera, " +
                            "then turn your head slightly for each one.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.samplesCaptured.toFloat() / state.totalSamples },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${state.samplesCaptured} / ${state.totalSamples} captured",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            (state.error ?: cameraError)?.let { message ->
                Banner(message, MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
            }
            state.message?.takeIf { state.error == null }?.let { message ->
                Banner(message, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
            }

            if (state.done) {
                Button(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Done")
                }
            } else {
                Button(
                    enabled = controller != null && !state.busy,
                    onClick = {
                        val camera = controller ?: return@Button
                        scope.launch {
                            cameraError = null
                            runCatching { camera.capture() }
                                .onSuccess(viewModel::onFrameCaptured)
                                .onFailure { cameraError = "Capture failed: ${it.message}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (controller == null) "Starting camera…" else "Capture shot ${state.samplesCaptured + 1}")
                }
            }
        }

        if (state.busy) BusyOverlay("Analysing face…")
    }
}

@Composable
private fun Banner(message: String, color: Color) {
    Surface(color = color, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        )
    }
}
