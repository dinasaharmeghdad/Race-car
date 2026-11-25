package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerProfile
import com.example.model.RacingCatalog
import com.example.model.TrackSpec
import com.example.model.formatTimeMs
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.ChampGold
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OrbitronFontFamily

@Composable
fun TrackSelectScreen(
    profile: PlayerProfile,
    selectedTrackId: String,
    useTiltSteering: Boolean,
    onSelectTrack: (String) -> Unit,
    onToggleTilt: (Boolean) -> Unit,
    onLaunchRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCar = RacingCatalog.carById(profile.selectedCarId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "انتخاب پیست مسابقه",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        RacingCatalog.tracks.forEach { track ->
            val bestLap = when (track.id) {
                "neon_tokyo" -> profile.bestLapNeonMs
                "alpine_pass" -> profile.bestLapAlpineMs
                "desert_canyon" -> profile.bestLapDesertMs
                else -> null
            }
            TrackCard(
                track = track,
                bestLapMs = bestLap,
                isSelected = track.id == selectedTrackId,
                onClick = { onSelectTrack(track.id) }
            )
        }

        // Steering Control Mode Toggle
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (useTiltSteering) Icons.Filled.ScreenRotation else Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = NitroCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = if (useTiltSteering) "فرمان با ژیروسکوپ (چرخش گوشی)" else "فرمان لمسی دکمه‌ای",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "در حین مسابقه نیز قابل تغییر است",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = useTiltSteering,
                    onCheckedChange = onToggleTilt,
                    modifier = Modifier.testTag("tilt_steering_switch")
                )
            }
        }

        Button(
            onClick = onLaunchRace,
            colors = ButtonDefaults.buttonColors(containerColor = ApexCrimson),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("start_3d_race_button")
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "شروع مسابقه با ${activeCar.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TrackCard(
    track: TrackSpec,
    bestLapMs: Long?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(166.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ApexCrimson else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.large
            )
            .clickable(onClick = onClick)
            .testTag("track_card_${track.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = track.thumbnailRes),
                contentDescription = track.nameFa,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.92f),
                                ObsidianBg.copy(alpha = 0.55f),
                                ObsidianBg.copy(alpha = 0.25f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isSelected) ApexCrimson else Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "${track.laps} دور • ${track.difficultyLabelFa}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = ChampGold.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = ChampGold, modifier = Modifier.size(14.dp))
                            Text(
                                text = "+${track.rewardBase} CR",
                                fontFamily = OrbitronFontFamily,
                                fontSize = 11.sp,
                                color = ChampGold
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = track.nameFa,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = track.subtitleFa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = NitroCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = "بهترین دور: ${formatTimeMs(bestLapMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NitroCyan
                        )
                    }
                }
            }
        }
    }
}
