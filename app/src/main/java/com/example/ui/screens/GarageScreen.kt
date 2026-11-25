package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine3d.GarageShowcaseView
import com.example.model.CarSetup
import com.example.model.CarSpec
import com.example.model.PlayerProfile
import com.example.model.RacingCatalog
import com.example.model.hexToRgbFloats
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.ChampGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RajdhaniFontFamily

@Composable
fun GarageScreen(
    profile: PlayerProfile,
    browsingCarId: String,
    garageMap: Map<String, CarSetup>,
    isBusy: Boolean,
    onBrowseCar: (String) -> Unit,
    onSelectCar: (String) -> Unit,
    onUnlockCar: (String) -> Unit,
    onGoToCustomize: () -> Unit,
    onGoToRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = RacingCatalog.carById(browsingCarId)
    val setup = garageMap[browsingCarId] ?: CarSetup.defaultFor(spec, profile.userId)
    val isUnlocked = profile.unlockedCarIds.contains(spec.id)
    val isSelected = profile.selectedCarId == spec.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 3D Interactive Showroom Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(275.dp)
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
        ) {
            GarageShowcaseView(
                carSpec = spec,
                carSetup = setup,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle top-left car title & class overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = spec.name.uppercase(),
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = spec.classLabelFa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NitroCyan
                )
            }

            // 360 Touch Hint Pill
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = null,
                        tint = NitroCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "چرخش ۳۶۰° با لمس",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            if (isSelected) {
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .border(1.dp, EmeraldSuccess, RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "خودروی فعال",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
                        )
                    }
                }
            }
        }

        // Horizontal Carousel of All 6 Diverse 3D Cars
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(RacingCatalog.cars, key = { it.id }) { car ->
                val unlocked = profile.unlockedCarIds.contains(car.id)
                val activeBrowse = car.id == browsingCarId
                val activeSelected = car.id == profile.selectedCarId
                val carSetup = garageMap[car.id] ?: CarSetup.defaultFor(car)
                val rgb = hexToRgbFloats(carSetup.paintColorHex)
                val swatchColor = Color(rgb[0], rgb[1], rgb[2])

                CarSelectorCard(
                    spec = car,
                    swatchColor = swatchColor,
                    isBrowsing = activeBrowse,
                    isUnlocked = unlocked,
                    isSelected = activeSelected,
                    onClick = { onBrowseCar(car.id) }
                )
            }
        }

        // Car Specs & Action Panel
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = spec.descriptionFa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBar("سرعت نهایی", setup.effectiveTopSpeed(spec), ApexCrimson)
                    StatBar("شتاب", setup.effectiveAcceleration(spec), ChampGold)
                    StatBar("فرمان‌پذیری", setup.effectiveHandling(spec), NitroCyan)
                    StatBar("قدرت نیترو", setup.effectiveNitro(spec), EmeraldSuccess)
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isUnlocked) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isSelected) {
                            Button(
                                onClick = { onSelectCar(spec.id) },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("select_car_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ApexCrimson)
                            ) {
                                Text("انتخاب برای مسابقه", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Button(
                                onClick = onGoToRace,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("garage_race_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ApexCrimson)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("رفتن به پیست", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isSelected) onSelectCar(spec.id)
                                onGoToCustomize()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("go_customize_button")
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("شخصی‌سازی", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    val canAfford = profile.credits >= spec.price
                    Button(
                        onClick = { onUnlockCar(spec.id) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) ChampGold else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canAfford) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("unlock_car_button")
                    ) {
                        Icon(
                            imageVector = if (canAfford) Icons.Filled.ShoppingCart else Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (canAfford) "خرید خودرو • ${spec.price} سکه" else "نیازمند ${spec.price} سکه",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarSelectorCard(
    spec: CarSpec,
    swatchColor: Color,
    isBrowsing: Boolean,
    isUnlocked: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isBrowsing -> ApexCrimson
        isSelected -> EmeraldSuccess
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = if (isBrowsing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .width(146.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(if (isBrowsing) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("car_card_${spec.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
                if (!isUnlocked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = ChampGold,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = spec.name,
                fontFamily = RajdhaniFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Text(
                text = if (isUnlocked) spec.classLabelFa else "${spec.price} سکه",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else ChampGold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StatBar(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(targetValue = (value / 100f).coerceIn(0f, 1f), label = "stat")
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "$value",
                fontFamily = OrbitronFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
        )
    }
}
