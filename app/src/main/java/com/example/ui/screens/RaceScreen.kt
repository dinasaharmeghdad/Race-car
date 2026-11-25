package com.example.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine3d.RaceGLSurfaceView
import com.example.engine3d.RaceInputState
import com.example.engine3d.RaceSurfaceView
import com.example.engine3d.RaceTelemetry
import com.example.model.CarSetup
import com.example.model.CarSpec
import com.example.model.TrackSpec
import com.example.model.formatTimeMs
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.ChampGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OrbitronFontFamily

@Composable
fun RaceScreen(
    carSpec: CarSpec,
    carSetup: CarSetup,
    trackSpec: TrackSpec,
    initialTiltSteering: Boolean,
    onRaceFinished: (position: Int, totalTimeMs: Long, bestLapMs: Long, creditsEarned: Long) -> Unit,
    onExitRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    var telemetry by remember { mutableStateOf(RaceTelemetry(totalLaps = trackSpec.laps)) }
    var isPaused by remember { mutableStateOf(false) }
    var useTilt by remember { mutableStateOf(initialTiltSteering) }
    var resultRecorded by remember { mutableStateOf(false) }

    var steerLeftPressed by remember { mutableStateOf(false) }
    var steerRightPressed by remember { mutableStateOf(false) }
    var gasPressed by remember { mutableStateOf(false) }
    var brakePressed by remember { mutableStateOf(false) }
    var nitroPressed by remember { mutableStateOf(false) }
    var tiltSteerValue by remember { mutableFloatStateOf(0f) }

    var glViewRef by remember { mutableStateOf<RaceGLSurfaceView?>(null) }

    // Hardware accelerometer tilt steering when enabled
    val context = LocalContext.current
    DisposableEffect(useTilt) {
        if (!useTilt) {
            tiltSteerValue = 0f
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Tilt along X axis in portrait or Y depending on natural orientation; map safely
                    val rawX = -event.values[0] / 5.5f
                    tiltSteerValue = rawX.coerceIn(-1f, 1f)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (accel != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val touchSteer = when {
        steerLeftPressed && !steerRightPressed -> -1f
        steerRightPressed && !steerLeftPressed -> 1f
        else -> 0f
    }
    val combinedSteer = if (useTilt && touchSteer == 0f) tiltSteerValue else touchSteer
    val inputState = RaceInputState(
        steer = combinedSteer,
        throttle = if (gasPressed) 1f else 0f,
        brake = if (brakePressed) 1f else 0f,
        nitro = nitroPressed
    )

    val creditsEarnedForPos = remember(telemetry.position, trackSpec.id) {
        when (telemetry.position) {
            1 -> (trackSpec.rewardBase * 1.5f).toLong()
            2 -> trackSpec.rewardBase
            3 -> (trackSpec.rewardBase * 0.65f).toLong()
            else -> (trackSpec.rewardBase * 0.40f).toLong()
        }
    }

    LaunchedEffect(telemetry.isFinished) {
        if (telemetry.isFinished && !resultRecorded) {
            resultRecorded = true
            onRaceFinished(
                telemetry.position,
                telemetry.totalTimeMs,
                telemetry.bestLapMs,
                creditsEarnedForPos
            )
        }
    }

    BackHandler {
        if (telemetry.isFinished) {
            onExitRace()
        } else {
            isPaused = !isPaused
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // 3D OpenGL ES Racing Surface
        RaceSurfaceView(
            carSpec = carSpec,
            carSetup = carSetup,
            trackSpec = trackSpec,
            inputState = inputState,
            isPaused = isPaused,
            onTelemetry = { telemetry = it },
            onViewReady = { glViewRef = it },
            modifier = Modifier.fillMaxSize()
        )

        // Racing HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Telemetry & Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left Telemetry Box (Position, Lap, Timer)
                Surface(
                    color = ObsidianBg.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.medium)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "POS ${telemetry.position}/4",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ChampGold
                            )
                            Text(
                                text = "LAP ${telemetry.currentLap}/${telemetry.totalLaps}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = formatTimeMs(telemetry.currentLapMs),
                            fontFamily = OrbitronFontFamily,
                            fontSize = 13.sp,
                            color = NitroCyan
                        )
                    }
                }

                // Right Cluster: Minimap + Camera / Tilt / Pause buttons
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimapWidget(
                        telemetry = telemetry,
                        modifier = Modifier.size(86.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudIconButton(
                            icon = Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.pause_race),
                            onClick = { isPaused = true },
                            modifier = Modifier.testTag("race_pause_button")
                        )
                        HudIconButton(
                            icon = Icons.Filled.Videocam,
                            contentDescription = "تغییر دوربین",
                            onClick = { glViewRef?.renderer?.cycleCameraMode() },
                            modifier = Modifier.testTag("race_camera_button")
                        )
                        HudIconButton(
                            icon = if (useTilt) Icons.Filled.ScreenRotation else Icons.Filled.TouchApp,
                            contentDescription = "تغییر نوع فرمان",
                            tint = if (useTilt) NitroCyan else Color.White,
                            onClick = { useTilt = !useTilt },
                            modifier = Modifier.testTag("race_tilt_toggle")
                        )
                    }
                }
            }

            // Center Countdown / Status Callouts
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    telemetry.countdownSec > 0 -> {
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.78f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(96.dp)
                                .border(2.dp, ApexCrimson, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${telemetry.countdownSec}",
                                    fontFamily = OrbitronFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 44.sp,
                                    color = if (telemetry.countdownSec == 1) EmeraldSuccess else ApexCrimson
                                )
                            }
                        }
                    }
                    telemetry.isNitroActive -> {
                        Surface(
                            color = NitroCyan.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.border(1.dp, NitroCyan, RoundedCornerShape(999.dp))
                        ) {
                            Text(
                                text = "NITRO BOOST",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = NitroCyan,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                    telemetry.isDrifting -> {
                        Surface(
                            color = ChampGold.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.border(1.dp, ChampGold, RoundedCornerShape(999.dp))
                        ) {
                            Text(
                                text = "DRIFT +NITRO",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ChampGold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Controls & Digital Speedometer Cluster
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Digital Speedometer + RPM + Nitro Bar
                Surface(
                    color = ObsidianBg.copy(alpha = 0.76f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.medium)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${telemetry.speedKmh}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                color = Color.White
                            )
                            Text(
                                text = "KM/H",
                                fontFamily = OrbitronFontFamily,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                            Text(
                                text = "G${telemetry.gear}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ChampGold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { telemetry.rpmNorm },
                            color = if (telemetry.rpmNorm > 0.85f) ApexCrimson else EmeraldSuccess,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier
                                .width(150.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                        LinearProgressIndicator(
                            progress = { telemetry.nitroNorm },
                            color = NitroCyan,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier
                                .width(150.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }

                // Multi-touch Ergonomic Pedals & Steering
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Left Steering Pads
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HoldControlButton(
                            label = null,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            accent = Color.White,
                            isPressed = steerLeftPressed,
                            onPressedChange = { steerLeftPressed = it },
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("steer_left_button")
                        )
                        HoldControlButton(
                            label = null,
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            accent = Color.White,
                            isPressed = steerRightPressed,
                            onPressedChange = { steerRightPressed = it },
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("steer_right_button")
                        )
                    }

                    // Right Nitro + Brake + Gas Pedals
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        HoldControlButton(
                            label = "NITRO",
                            icon = Icons.Filled.Bolt,
                            accent = NitroCyan,
                            isPressed = nitroPressed,
                            onPressedChange = { nitroPressed = it },
                            modifier = Modifier
                                .width(66.dp)
                                .height(68.dp)
                                .testTag("nitro_button")
                        )
                        HoldControlButton(
                            label = "ترمز",
                            icon = null,
                            accent = ApexCrimson,
                            isPressed = brakePressed,
                            onPressedChange = { brakePressed = it },
                            modifier = Modifier
                                .width(64.dp)
                                .height(68.dp)
                                .testTag("brake_button")
                        )
                        HoldControlButton(
                            label = "گاز",
                            icon = null,
                            accent = EmeraldSuccess,
                            isPressed = gasPressed,
                            onPressedChange = { gasPressed = it },
                            modifier = Modifier
                                .width(72.dp)
                                .height(86.dp)
                                .testTag("gas_button")
                        )
                    }
                }
            }
        }

        // Pause Modal Overlay
        if (isPaused && !telemetry.isFinished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(0.86f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("مسابقه متوقف شد", style = MaterialTheme.typography.headlineMedium)
                        Button(
                            onClick = { isPaused = false },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.resume_race), style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = {
                                resultRecorded = false
                                glViewRef?.renderer?.restartRace()
                                isPaused = false
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.restart_race), style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onExitRace,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexCrimson),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text(stringResource(R.string.exit_to_garage), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Post-Race Podium Summary Overlay
        if (telemetry.isFinished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(0.90f)
                        .border(2.dp, ChampGold.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = if (telemetry.position == 1) ChampGold else NitroCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (telemetry.position == 1) "قهرمان مسابقه!" else "پایان مسابقه • رتبه ${telemetry.position}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (telemetry.position == 1) ChampGold else Color.White
                        )
                        Text(
                            text = "${trackSpec.nameFa} • ${carSpec.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SummaryStatRow("زمان کل", formatTimeMs(telemetry.totalTimeMs), Color.White)
                                SummaryStatRow("بهترین دور", formatTimeMs(telemetry.bestLapMs), NitroCyan)
                                SummaryStatRow("جایزه دریافتی", "+$creditsEarnedForPos CR", ChampGold)
                            }
                        }

                        Button(
                            onClick = {
                                resultRecorded = false
                                glViewRef?.renderer?.restartRace()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ApexCrimson),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("مسابقه مجدد", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onExitRace,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text(stringResource(R.string.exit_to_garage), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = valueColor)
    }
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Surface(
        color = ObsidianBg.copy(alpha = 0.72f),
        shape = CircleShape,
        modifier = modifier
            .size(48.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HoldControlButton(
    label: String?,
    icon: ImageVector?,
    accent: Color,
    isPressed: Boolean,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isPressed) accent.copy(alpha = 0.38f) else ObsidianBg.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .border(if (isPressed) 2.dp else 1.2.dp, accent.copy(alpha = if (isPressed) 0.95f else 0.45f), MaterialTheme.shapes.medium)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressedChange(true)
                    waitForUpOrCancellation()
                    onPressedChange(false)
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = label ?: "Control", tint = accent, modifier = Modifier.size(26.dp))
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun MinimapWidget(
    telemetry: RaceTelemetry,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val pts = telemetry.minimapTrack
            if (pts.size >= 2) {
                val path = Path()
                pts.forEachIndexed { idx, (nx, nz) ->
                    val px = nx * size.width
                    val py = nz * size.height
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.45f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
            telemetry.minimapCars.forEach { dot ->
                val center = Offset(dot.normX * size.width, dot.normZ * size.height)
                drawCircle(
                    color = if (dot.isPlayer) ApexCrimson else NitroCyan,
                    radius = if (dot.isPlayer) 6.5f else 4.2f,
                    center = center
                )
            }
        }
    }
}
