package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine3d.GarageShowcaseView
import com.example.model.CarSetup
import com.example.model.PaintFinish
import com.example.model.PlayerProfile
import com.example.model.RacingCatalog
import com.example.model.RimStyle
import com.example.model.SpoilerStyle
import com.example.model.hexToRgbFloats
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.ChampGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.OrbitronFontFamily
import com.example.viewmodel.UpgradePart

@Composable
fun CustomizeScreen(
    profile: PlayerProfile,
    browsingCarId: String,
    activeSetup: CarSetup,
    hasUnsavedDraft: Boolean,
    isBusy: Boolean,
    onPaintChanged: (String) -> Unit,
    onFinishChanged: (PaintFinish) -> Unit,
    onRimStyleChanged: (RimStyle) -> Unit,
    onRimColorChanged: (String) -> Unit,
    onSpoilerChanged: (SpoilerStyle) -> Unit,
    onUnderglowChanged: (String) -> Unit,
    onUpgradePart: (UpgradePart) -> Unit,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = RacingCatalog.carById(browsingCarId)
    val isUnlocked = profile.unlockedCarIds.contains(spec.id)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("رنگ و پوشش", "رینگ", "اسپویلر", "نئون زیر خودرو", "تیونینگ فنی")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live 3D Preview of all customizations
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(245.dp)
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
        ) {
            GarageShowcaseView(
                carSpec = spec,
                carSetup = activeSetup,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = spec.name,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Studio Category Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ApexCrimson,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == idx) ApexCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        Text("رنگ بدنه", style = MaterialTheme.typography.titleMedium)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(RacingCatalog.paintPalette) { swatch ->
                                val rgb = hexToRgbFloats(swatch.hex)
                                ColorCircleItem(
                                    color = Color(rgb[0], rgb[1], rgb[2]),
                                    label = swatch.nameFa,
                                    selected = activeSetup.paintColorHex.equals(swatch.hex, ignoreCase = true),
                                    onClick = { onPaintChanged(swatch.hex) }
                                )
                            }
                        }

                        Text("نوع پوشش و متریال", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaintFinish.entries.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { finish ->
                                        OptionChip(
                                            label = finish.labelFa,
                                            selected = activeSetup.finishEnum == finish,
                                            onClick = { onFinishChanged(finish) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Text("طراحی سه‌بعدی رینگ", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RimStyle.entries.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { rim ->
                                        OptionChip(
                                            label = rim.labelFa,
                                            selected = activeSetup.rimEnum == rim,
                                            onClick = { onRimStyleChanged(rim) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Text("رنگ رینگ", style = MaterialTheme.typography.titleMedium)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(RacingCatalog.rimPalette) { swatch ->
                                val rgb = hexToRgbFloats(swatch.hex)
                                ColorCircleItem(
                                    color = Color(rgb[0], rgb[1], rgb[2]),
                                    label = swatch.nameFa,
                                    selected = activeSetup.rimColorHex.equals(swatch.hex, ignoreCase = true),
                                    onClick = { onRimColorChanged(swatch.hex) }
                                )
                            }
                        }
                    }

                    2 -> {
                        Text("اسپویلر عقب (آیرودینامیک)", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpoilerStyle.entries.forEach { spoiler ->
                                OptionChip(
                                    label = spoiler.labelFa,
                                    selected = activeSetup.spoilerEnum == spoiler,
                                    onClick = { onSpoilerChanged(spoiler) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    3 -> {
                        Text("نورپردازی نئون زیر بدنه", style = MaterialTheme.typography.titleMedium)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(RacingCatalog.underglowPalette) { swatch ->
                                val c = if (swatch.hex == "NONE") Color(0xFF222834) else {
                                    val rgb = hexToRgbFloats(swatch.hex)
                                    Color(rgb[0], rgb[1], rgb[2])
                                }
                                ColorCircleItem(
                                    color = c,
                                    label = swatch.nameFa,
                                    selected = activeSetup.underglowHex.equals(swatch.hex, ignoreCase = true),
                                    onClick = { onUnderglowChanged(swatch.hex) }
                                )
                            }
                        }
                    }

                    4 -> {
                        Text("ارتقای عملکرد فنی", style = MaterialTheme.typography.titleMedium)
                        UpgradeRow(
                            part = UpgradePart.ENGINE,
                            level = activeSetup.engineLevel.toInt(),
                            credits = profile.credits,
                            isUnlocked = isUnlocked,
                            isBusy = isBusy,
                            onUpgrade = { onUpgradePart(UpgradePart.ENGINE) }
                        )
                        UpgradeRow(
                            part = UpgradePart.TURBO,
                            level = activeSetup.turboLevel.toInt(),
                            credits = profile.credits,
                            isUnlocked = isUnlocked,
                            isBusy = isBusy,
                            onUpgrade = { onUpgradePart(UpgradePart.TURBO) }
                        )
                        UpgradeRow(
                            part = UpgradePart.TIRES,
                            level = activeSetup.tiresLevel.toInt(),
                            credits = profile.credits,
                            isUnlocked = isUnlocked,
                            isBusy = isBusy,
                            onUpgrade = { onUpgradePart(UpgradePart.TIRES) }
                        )
                        UpgradeRow(
                            part = UpgradePart.NITRO,
                            level = activeSetup.nitroLevel.toInt(),
                            credits = profile.credits,
                            isUnlocked = isUnlocked,
                            isBusy = isBusy,
                            onUpgrade = { onUpgradePart(UpgradePart.NITRO) }
                        )
                    }
                }
            }
        }

        if (hasUnsavedDraft) {
            if (!isUnlocked) {
                Text(
                    text = "این خودرو هنوز خریداری نشده است — ابتدا از بخش گاراژ آن را بخرید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChampGold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDiscardChanges,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("discard_customization_button")
                    ) {
                        Text("لغو", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onSaveChanges,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.Black),
                        modifier = Modifier
                            .weight(2f)
                            .height(50.dp)
                            .testTag("save_customization_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ذخیره تغییرات در گاراژ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorCircleItem(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.5.dp,
                    color = if (selected) NitroCyan else Color.White.copy(alpha = 0.35f),
                    shape = CircleShape
                )
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = label,
                    tint = if (color.red + color.green + color.blue > 1.8f) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) NitroCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ApexCrimson.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) ApexCrimson else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun UpgradeRow(
    part: UpgradePart,
    level: Int,
    credits: Long,
    isUnlocked: Boolean,
    isBusy: Boolean,
    onUpgrade: () -> Unit
) {
    val cost = RacingCatalog.upgradeCost(level)
    val canUpgrade = isUnlocked && level < 5 && credits >= cost && !isBusy

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(part.labelFa, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "LVL $level/5",
                        fontFamily = OrbitronFontFamily,
                        fontSize = 11.sp,
                        color = NitroCyan
                    )
                }
                Text(
                    text = part.descFa,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Stage pips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..5) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (i <= level) ChampGold else Color.White.copy(alpha = 0.14f))
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            if (level >= 5) {
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "حداکثر",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                Button(
                    onClick = onUpgrade,
                    enabled = canUpgrade,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampGold,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("$cost سکه", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
