package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutUser
import com.example.model.CarSetup
import com.example.model.RacingCatalog
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.ChampGold
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.OrbitronFontFamily
import com.example.viewmodel.GarageViewModel
import com.example.viewmodel.UiState

private enum class HubTab(val labelRes: Int, val icon: ImageVector) {
    GARAGE(R.string.nav_garage, Icons.Filled.DirectionsCar),
    CUSTOMIZE(R.string.nav_customize, Icons.Filled.Build),
    RACE(R.string.nav_race, Icons.Filled.Flag),
    RECORDS(R.string.nav_records, Icons.Filled.EmojiEvents)
}

@Composable
fun MainHubScreen(
    viewModel: GarageViewModel,
    modifier: Modifier = Modifier
) {
    val profileUiState by viewModel.profileState.collectAsStateWithLifecycle()
    val garageMap by viewModel.garageMapState.collectAsStateWithLifecycle()
    val recentRaces by viewModel.recentRacesState.collectAsStateWithLifecycle()
    val browsingCarId by viewModel.browsingCarId.collectAsStateWithLifecycle()
    val draftSetup by viewModel.draftSetup.collectAsStateWithLifecycle()
    val selectedTrackId by viewModel.selectedTrackId.collectAsStateWithLifecycle()
    val useTiltSteering by viewModel.useTiltSteering.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var currentTabIdx by rememberSaveable { mutableIntStateOf(0) }
    var isRacing by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val state = profileUiState) {
        is UiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ApexCrimson)
            }
        }
        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ApexCrimson
                )
            }
        }
        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ApexCrimson)
                }
                return
            }

            if (isRacing) {
                val raceCarSpec = RacingCatalog.carById(profile.selectedCarId)
                val raceCarSetup = garageMap[raceCarSpec.id] ?: CarSetup.defaultFor(raceCarSpec, profile.userId)
                val raceTrack = RacingCatalog.trackById(selectedTrackId)
                RaceScreen(
                    carSpec = raceCarSpec,
                    carSetup = raceCarSetup,
                    trackSpec = raceTrack,
                    initialTiltSteering = useTiltSteering,
                    onRaceFinished = { pos, totalMs, bestLapMs, credits ->
                        viewModel.recordFinishedRace(
                            trackId = raceTrack.id,
                            carId = raceCarSpec.id,
                            position = pos,
                            totalTimeMs = totalMs,
                            bestLapMs = bestLapMs,
                            creditsEarned = credits
                        )
                    },
                    onExitRace = { isRacing = false }
                )
                return
            }

            val currentTab = HubTab.entries[currentTabIdx]
            val activeCarSetup = draftSetup?.takeIf { it.carId == browsingCarId }
                ?: garageMap[browsingCarId]
                ?: CarSetup.defaultFor(RacingCatalog.carById(browsingCarId), profile.userId)

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val useRail = maxWidth >= 680.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "APEX RACING",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.border(1.dp, ChampGold.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MonetizationOn,
                                        contentDescription = "Credits",
                                        tint = ChampGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${profile.credits} CR",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ChampGold
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                HubTab.entries.forEachIndexed { idx, tab ->
                                    val selected = currentTabIdx == idx
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTabIdx = idx },
                                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                        label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ApexCrimson,
                                            selectedTextColor = ApexCrimson,
                                            indicatorColor = ApexCrimson.copy(alpha = 0.16f)
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                HubTab.entries.forEachIndexed { idx, tab ->
                                    NavigationRailItem(
                                        selected = currentTabIdx == idx,
                                        onClick = { currentTabIdx = idx },
                                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = ApexCrimson,
                                            selectedTextColor = ApexCrimson,
                                            indicatorColor = ApexCrimson.copy(alpha = 0.16f)
                                        )
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                                when (currentTab) {
                                    HubTab.GARAGE -> GarageScreen(
                                        profile = profile,
                                        browsingCarId = browsingCarId,
                                        garageMap = garageMap,
                                        isBusy = isBusy,
                                        onBrowseCar = viewModel::browseCar,
                                        onSelectCar = viewModel::selectCarForRace,
                                        onUnlockCar = viewModel::unlockCar,
                                        onGoToCustomize = { currentTabIdx = HubTab.CUSTOMIZE.ordinal },
                                        onGoToRace = { currentTabIdx = HubTab.RACE.ordinal }
                                    )

                                    HubTab.CUSTOMIZE -> CustomizeScreen(
                                        profile = profile,
                                        browsingCarId = browsingCarId,
                                        activeSetup = activeCarSetup,
                                        hasUnsavedDraft = draftSetup != null && draftSetup?.carId == browsingCarId,
                                        isBusy = isBusy,
                                        onPaintChanged = viewModel::updateDraftPaint,
                                        onFinishChanged = viewModel::updateDraftFinish,
                                        onRimStyleChanged = viewModel::updateDraftRimStyle,
                                        onRimColorChanged = viewModel::updateDraftRimColor,
                                        onSpoilerChanged = viewModel::updateDraftSpoiler,
                                        onUnderglowChanged = viewModel::updateDraftUnderglow,
                                        onUpgradePart = { part -> viewModel.upgradeCarPart(browsingCarId, part) },
                                        onSaveChanges = viewModel::saveDraftCustomization,
                                        onDiscardChanges = viewModel::discardDraft
                                    )

                                    HubTab.RACE -> TrackSelectScreen(
                                        profile = profile,
                                        selectedTrackId = selectedTrackId,
                                        useTiltSteering = useTiltSteering,
                                        onSelectTrack = viewModel::selectTrack,
                                        onToggleTilt = viewModel::setTiltSteering,
                                        onLaunchRace = { isRacing = true }
                                    )

                                    HubTab.RECORDS -> ProfileRecordsScreen(
                                        profile = profile,
                                        recentRaces = recentRaces,
                                        onSignOut = {
                                            signOutUser(credentialManager, scope)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
