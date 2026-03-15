package ai.fixitbuddy.app.features.session

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ai.fixitbuddy.app.R
import ai.fixitbuddy.app.core.camera.GlassesState
import ai.fixitbuddy.app.features.session.components.CameraViewfinder
import ai.fixitbuddy.app.features.session.components.GenieAvatar
import ai.fixitbuddy.app.features.session.components.GenieTranscript
import ai.fixitbuddy.app.features.session.components.StatusIndicator
import ai.fixitbuddy.app.features.session.components.ToolCallChip

@Composable
fun SessionScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    // Permission state
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    if (!permissionsGranted) {
        PermissionScreen(
            onRequestPermissions = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }
        )
        return
    }

    // Main session UI
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera viewfinder (full screen background)
        CameraViewfinder(
            cameraManager = viewModel.cameraManager,
            modifier = Modifier.fillMaxSize()
        )

        // Update torch availability after camera starts
        LaunchedEffect(Unit) {
            delay(1000)
            viewModel.updateTorchAvailability()
        }

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.sessionState != SessionState.Idle || uiState.errorMessage != null) {
                StatusIndicator(
                    sessionState = uiState.sessionState,
                    agentState = uiState.agentState
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }

        // Tool call chip — shows briefly when the agent invokes a tool
        ToolCallChip(
            toolName = uiState.lastToolCall,
            toolCallCount = uiState.toolCallCount,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 56.dp)
        )

        // Idle state guidance overlay — frosted dark card for legibility on any camera feed
        AnimatedVisibility(
            visible = uiState.sessionState == SessionState.Idle && uiState.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC080810))
                    .border(1.dp, Color(0x33FF6A1E), RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_genie_lamp),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.session_idle_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF6A1E)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.session_idle_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xCCF0F0F5),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        // Genie Panel — chat bubbles + compact controls (Active or Connecting)
        AnimatedVisibility(
            visible = uiState.sessionState == SessionState.Active ||
                      uiState.sessionState == SessionState.Connecting,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Chat panel — gradient fade from camera feed into conversation area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.28f to Color(0xE6080810),
                                    0.55f to Color(0xFB080810),
                                    1f to Color(0xFF080810)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        GenieAvatar(
                            sessionState = uiState.sessionState,
                            agentState = uiState.agentState,
                            audioLevel = audioLevel,
                            modifier = Modifier.padding(bottom = 8.dp, end = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0x40121624))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        ) {
                            GenieTranscript(
                                chatTurns = uiState.chatTurns,
                                isGenieStreaming = uiState.agentState == "speaking",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Compact control strip — 52dp
                // Hairline orange divider above the strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0x55FF6A1E), Color.Transparent)
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13131F))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (uiState.hasTorch) {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.toggleFlashlight()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (uiState.isTorchOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.1f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = stringResource(R.string.flashlight_toggle),
                                    tint = if (uiState.isTorchOn) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        val isGlasses = uiState.cameraSource == CameraSource.GLASSES
                        val glassesIconTint = when {
                            isGlasses && uiState.glassesState == GlassesState.STREAMING -> MaterialTheme.colorScheme.primary
                            isGlasses -> MaterialTheme.colorScheme.secondary
                            else -> Color.White
                        }
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.switchCameraSource(if (isGlasses) CameraSource.PHONE else CameraSource.GLASSES)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isGlasses) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else Color.White.copy(alpha = 0.1f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isGlasses) Icons.Default.Visibility else Icons.Default.Smartphone,
                                contentDescription = stringResource(if (isGlasses) R.string.camera_source_glasses else R.string.camera_source_phone),
                                tint = glassesIconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // End session pill
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.stopSession()
                        },
                        modifier = Modifier.height(34.dp).width(88.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.stop_session), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Start button — shown when Idle or Error
        AnimatedVisibility(
            visible = uiState.sessionState == SessionState.Idle || uiState.sessionState == SessionState.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.startSession()
                    },
                    modifier = Modifier.height(56.dp).width(200.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.start_session))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_session), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Error snackbar with auto-dismiss
        // Hoist padding calc above the let block so session state changes don't restart LaunchedEffect
        val snackbarBottomPadding = if (uiState.sessionState == SessionState.Active ||
            uiState.sessionState == SessionState.Connecting) 252.dp else 72.dp
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                delay(5000)
                viewModel.dismissError()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = snackbarBottomPadding)
                    .padding(horizontal = 16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun PermissionScreen(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.permission_main_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PermissionItem(
                    title = stringResource(R.string.permission_camera_title),
                    description = stringResource(R.string.permission_camera_body)
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionItem(
                    title = stringResource(R.string.permission_audio_title),
                    description = stringResource(R.string.permission_audio_body)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(stringResource(R.string.permission_grant))
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
