package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RacingRepository
import com.example.model.CarSetup
import com.example.model.PaintFinish
import com.example.model.PlayerProfile
import com.example.model.RaceResult
import com.example.model.RacingCatalog
import com.example.model.RimStyle
import com.example.model.SpoilerStyle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UpgradePart(val labelFa: String, val descFa: String) {
    ENGINE("موتور", "افزایش سقف سرعت نهایی"),
    TURBO("توربوشارژ", "افزایش شتاب‌گیری و واکنش گاز"),
    TIRES("لاستیک و تعلیق", "بهبود چسبندگی و فرمان‌پذیری در پیچ‌ها"),
    NITRO("سیستم نیترو", "قدرت بیشتر و مصرف کمتر نیترو")
}

class GarageViewModel(
    private val repository: RacingRepository,
    private val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    val profileState: StateFlow<UiState<PlayerProfile?>> = repository.observeProfile(currentUserId)
        .map<PlayerProfile?, UiState<PlayerProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.message ?: "خطا در دریافت پروفایل"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val garageMapState: StateFlow<Map<String, CarSetup>> = repository.observeGarage(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing garage", firestoreError ?: error)
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val recentRacesState: StateFlow<List<RaceResult>> = repository.observeRecentRaces(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing races", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _browsingCarId = MutableStateFlow("viper_gtr")
    val browsingCarId: StateFlow<String> = _browsingCarId.asStateFlow()

    private val _draftSetup = MutableStateFlow<CarSetup?>(null)
    val draftSetup: StateFlow<CarSetup?> = _draftSetup.asStateFlow()

    private val _selectedTrackId = MutableStateFlow("neon_tokyo")
    val selectedTrackId: StateFlow<String> = _selectedTrackId.asStateFlow()

    private val _useTiltSteering = MutableStateFlow(false)
    val useTiltSteering: StateFlow<Boolean> = _useTiltSteering.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.ensurePlayerInitialized(initialDisplayName).onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "خطا در راه‌اندازی حساب")
            }
        }
    }

    fun browseCar(carId: String) {
        _browsingCarId.value = carId
        _draftSetup.value = null
    }

    fun selectTrack(trackId: String) {
        _selectedTrackId.value = trackId
    }

    fun setTiltSteering(enabled: Boolean) {
        _useTiltSteering.value = enabled
    }

    fun activeSetupFor(carId: String): CarSetup {
        val draft = _draftSetup.value
        if (draft != null && draft.carId == carId) return draft
        return garageMapState.value[carId] ?: CarSetup.defaultFor(RacingCatalog.carById(carId), currentUserId)
    }

    fun updateDraftPaint(hex: String) {
        val base = activeSetupFor(_browsingCarId.value)
        _draftSetup.value = base.copy(paintColorHex = hex)
    }

    fun updateDraftFinish(finish: PaintFinish) {
        val base = activeSetupFor(_browsingCarId.value)
        _draftSetup.value = base.copy(paintFinish = finish.name)
    }

    fun updateDraftRimStyle(rim: RimStyle) {
        val base = activeSetupFor(_browsingCarId.value)
        _draftSetup.value = base.copy(rimStyle = rim.name)
    }

    fun updateDraftRimColor(hex: String) {
        val base = activeSetupFor(_browsingCarId.value)
        _draftSetup.value = base.copy(rimColorHex = hex)
    }

    fun updateDraftSpoiler(spoiler: SpoilerStyle) {
        val base = activeSetupFor(_browsingCarId.value)
        _draftSetup.value = base.copy(spoilerStyle = spoiler.name)
    }

    fun updateDraftUnderglow(hex: String) {
        val base = activeSetupFor(_browsingCarId.value)
        _draftSetup.value = base.copy(underglowHex = hex)
    }

    fun discardDraft() {
        _draftSetup.value = null
    }

    fun saveDraftCustomization() {
        val draft = _draftSetup.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveCarSetup(draft)
                .onSuccess {
                    _draftSetup.value = null
                    _messages.tryEmit("شخصی‌سازی خودرو با موفقیت ذخیره شد!")
                }
                .onFailure {
                    _messages.tryEmit("خطا در ذخیره شخصی‌سازی: ${it.localizedMessage ?: ""}")
                }
            _isBusy.value = false
        }
    }

    fun selectCarForRace(carId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.selectCar(carId)
                .onSuccess { _messages.tryEmit("${RacingCatalog.carById(carId).name} برای مسابقه انتخاب شد.") }
                .onFailure { _messages.tryEmit("خطا در انتخاب خودرو") }
            _isBusy.value = false
        }
    }

    fun unlockCar(carId: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        val spec = RacingCatalog.carById(carId)
        if (profile.credits < spec.price) {
            _messages.tryEmit("اعتبار کافی نیست! برای خرید این خودرو به ${spec.price} سکه نیاز دارید.")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.unlockCar(carId, profile)
                .onSuccess { _messages.tryEmit("${spec.name} با موفقیت خریداری و به گاراژ اضافه شد!") }
                .onFailure { _messages.tryEmit("خطا در خرید خودرو") }
            _isBusy.value = false
        }
    }

    fun upgradeCarPart(carId: String, part: UpgradePart) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        if (!profile.unlockedCarIds.contains(carId)) {
            _messages.tryEmit("ابتدا باید این خودرو را خریداری کنید.")
            return
        }
        val current = activeSetupFor(carId)
        val currentLevel = when (part) {
            UpgradePart.ENGINE -> current.engineLevel.toInt()
            UpgradePart.TURBO -> current.turboLevel.toInt()
            UpgradePart.TIRES -> current.tiresLevel.toInt()
            UpgradePart.NITRO -> current.nitroLevel.toInt()
        }
        if (currentLevel >= 5) {
            _messages.tryEmit("این قطعه به حداکثر سطح رسیده است.")
            return
        }
        val cost = RacingCatalog.upgradeCost(currentLevel)
        if (profile.credits < cost) {
            _messages.tryEmit("اعتبار کافی نیست! هزینه ارتقا: $cost سکه")
            return
        }
        val updated = when (part) {
            UpgradePart.ENGINE -> current.copy(engineLevel = (currentLevel + 1).toLong())
            UpgradePart.TURBO -> current.copy(turboLevel = (currentLevel + 1).toLong())
            UpgradePart.TIRES -> current.copy(tiresLevel = (currentLevel + 1).toLong())
            UpgradePart.NITRO -> current.copy(nitroLevel = (currentLevel + 1).toLong())
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveCarSetup(updated, upgradeCost = cost, currentCredits = profile.credits)
                .onSuccess {
                    _draftSetup.value = null
                    _messages.tryEmit("${part.labelFa} به سطح ${currentLevel + 1} ارتقا یافت!")
                }
                .onFailure {
                    _messages.tryEmit("خطا در ارتقای قطعه")
                }
            _isBusy.value = false
        }
    }

    fun recordFinishedRace(
        trackId: String,
        carId: String,
        position: Int,
        totalTimeMs: Long,
        bestLapMs: Long,
        creditsEarned: Long
    ) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.recordRaceCompletion(
                currentProfile = profile,
                trackId = trackId,
                carId = carId,
                position = position,
                totalTimeMs = totalTimeMs.coerceAtLeast(1000L),
                bestLapMs = bestLapMs.coerceAtLeast(500L),
                creditsEarned = creditsEarned
            ).onFailure {
                _messages.tryEmit("خطا در ثبت نتیجه مسابقه")
            }
        }
    }

    private companion object {
        const val TAG = "GarageVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
