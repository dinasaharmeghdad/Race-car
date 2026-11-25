package com.example.engine3d

import com.example.model.CarSetup
import com.example.model.CarSpec
import com.example.model.PaintFinish
import com.example.model.RacingCatalog
import com.example.model.RimStyle
import com.example.model.SpoilerStyle
import com.example.model.TrackSpec
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class CameraMode(val labelFa: String) {
    CHASE("دوربین تعقیب"),
    HOOD("دوربین کاپوت"),
    FAR("دوربین سینمایی")
}

data class RaceInputState(
    val steer: Float = 0f,       // -1f (left) to +1f (right)
    val throttle: Float = 0f,    // 0f..1f
    val brake: Float = 0f,       // 0f..1f
    val nitro: Boolean = false
)

data class MinimapCarDot(
    val normX: Float,
    val normZ: Float,
    val isPlayer: Boolean
)

data class RaceTelemetry(
    val speedKmh: Int = 0,
    val rpmNorm: Float = 0.15f,
    val gear: Int = 1,
    val nitroNorm: Float = 1f,
    val currentLap: Int = 1,
    val totalLaps: Int = 3,
    val position: Int = 1,
    val currentLapMs: Long = 0L,
    val bestLapMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val countdownSec: Int = 3, // 3,2,1,0 (0 = GO!)
    val isFinished: Boolean = false,
    val isDrifting: Boolean = false,
    val isNitroActive: Boolean = false,
    val cameraMode: CameraMode = CameraMode.CHASE,
    val minimapTrack: List<Pair<Float, Float>> = emptyList(),
    val minimapCars: List<MinimapCarDot> = emptyList()
)

class RacerEntity(
    val isPlayer: Boolean,
    val spec: CarSpec,
    val setup: CarSetup,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var yawRad: Float = 0f,
    var speed: Float = 0f,           // m/s
    var steerVisual: Float = 0f,
    var wheelSpinRad: Float = 0f,
    var pitchRad: Float = 0f,
    var rollRad: Float = 0f,
    var nitroFuel: Float = 1.0f,
    var isNitroActive: Boolean = false,
    var isDrifting: Boolean = false,
    var closestSampleIdx: Int = 0,
    var lap: Int = 1,
    var lastT: Float = 0f,
    var totalProgress: Float = 0f,
    var lapStartMs: Long = 0L,
    var bestLapMs: Long = Long.MAX_VALUE,
    var finishTimeMs: Long = 0L,
    var finished: Boolean = false,
    val aiTargetLaneOffset: Float = 0f,
    val aiBaseSkill: Float = 1f
)

class RaceSimulation(
    val playerSpec: CarSpec,
    val playerSetup: CarSetup,
    val trackSpec: TrackSpec,
    val builtTrack: BuiltTrack
) {
    val racers: List<RacerEntity>

    var countdownTimerSec: Float = 3.4f
    var raceStarted: Boolean = false
    var raceElapsedMs: Long = 0L
    var isPaused: Boolean = false
    var cameraMode: CameraMode = CameraMode.CHASE

    private var minX = 0f
    private var minZ = 0f
    private var span = 1f

    init {
        var mx = Float.MAX_VALUE; var xx = -Float.MAX_VALUE
        var mz = Float.MAX_VALUE; var xz = -Float.MAX_VALUE
        for (s in builtTrack.samples) {
            if (s.pos.x < mx) mx = s.pos.x
            if (s.pos.x > xx) xx = s.pos.x
            if (s.pos.z < mz) mz = s.pos.z
            if (s.pos.z > xz) xz = s.pos.z
        }
        minX = mx; minZ = mz
        span = maxOf(xx - mx, xz - mz).coerceAtLeast(1f)

        val rivalSpecs = RacingCatalog.cars.filter { it.id != playerSpec.id }.take(3)
        val rivalColors = listOf("#00E5FF", "#FFB800", "#9D4EDD")

        val entities = mutableListOf<RacerEntity>()

        // Place 4 cars on a staggered 2x2 starting grid just behind start line
        val n = builtTrack.samples.size
        val gridOffsets = listOf(
            Pair(-3.4f, n - 4), // Player P4 grid slot
            Pair(3.4f, n - 3),  // AI 1
            Pair(-3.4f, n - 2), // AI 2
            Pair(3.4f, n - 1)   // AI 3
        )

        // Player
        val pSample = builtTrack.samples[gridOffsets[0].second]
        val pPos = pSample.pos + pSample.right * gridOffsets[0].first
        val pYaw = atan2(pSample.tangent.x, pSample.tangent.z)
        entities.add(
            RacerEntity(
                isPlayer = true,
                spec = playerSpec,
                setup = playerSetup,
                x = pPos.x,
                y = pPos.y,
                z = pPos.z,
                yawRad = pYaw,
                closestSampleIdx = gridOffsets[0].second,
                lastT = pSample.t
            )
        )

        // 3 AI Rivals
        rivalSpecs.forEachIndexed { idx, rSpec ->
            val g = gridOffsets[idx + 1]
            val s = builtTrack.samples[g.second]
            val pos = s.pos + s.right * g.first
            val yaw = atan2(s.tangent.x, s.tangent.z)
            val rSetup = CarSetup(
                carId = rSpec.id,
                paintColorHex = rivalColors[idx % rivalColors.size],
                paintFinish = PaintFinish.METALLIC.name,
                rimStyle = RimStyle.TURBINE_10.name,
                rimColorHex = "#D9DFE8",
                spoilerStyle = SpoilerStyle.GT_WING.name,
                underglowHex = if (trackSpec.isNight) rivalColors[idx % rivalColors.size] else "NONE",
                engineLevel = 2L,
                turboLevel = 2L,
                tiresLevel = 2L,
                nitroLevel = 2L
            )
            entities.add(
                RacerEntity(
                    isPlayer = false,
                    spec = rSpec,
                    setup = rSetup,
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    yawRad = yaw,
                    closestSampleIdx = g.second,
                    lastT = s.t,
                    aiTargetLaneOffset = (idx - 1) * 3.0f,
                    aiBaseSkill = trackSpec.aiSpeedFactor * (0.95f + idx * 0.045f)
                )
            )
        }
        racers = entities
    }

    val player: RacerEntity get() = racers.first()

    fun step(dt: Float, input: RaceInputState) {
        if (isPaused) return

        if (!raceStarted) {
            countdownTimerSec -= dt
            if (countdownTimerSec <= 0f) {
                countdownTimerSec = 0f
                raceStarted = true
                racers.forEach { it.lapStartMs = 0L }
            }
            return
        }

        if (!player.finished) {
            raceElapsedMs += (dt * 1000f).toLong()
        }

        for (r in racers) {
            if (r.isPlayer) {
                stepPlayer(r, dt, input)
            } else {
                stepAi(r, dt)
            }
            updateTrackProgress(r)
        }
        resolveCarCollisions()
    }

    private fun stepPlayer(r: RacerEntity, dt: Float, input: RaceInputState) {
        if (r.finished) {
            r.speed *= (1f - dt * 1.4f).coerceAtLeast(0f)
            r.isNitroActive = false
            moveAlongHeading(r, dt)
            return
        }

        val topSpeedStat = r.setup.effectiveTopSpeed(r.spec) / 100f
        val accelStat = r.setup.effectiveAcceleration(r.spec) / 100f
        val handlingStat = r.setup.effectiveHandling(r.spec) / 100f
        val nitroStat = r.setup.effectiveNitro(r.spec) / 100f

        val maxSpeed = 48f + topSpeedStat * 28f // up to ~76 m/s (~275 km/h)
        val accelForce = 18f + accelStat * 18f
        val turnRate = 1.65f + handlingStat * 0.75f

        val usingNitro = input.nitro && r.nitroFuel > 0.02f && input.brake < 0.3f && r.speed > 4f
        r.isNitroActive = usingNitro
        if (usingNitro) {
            val drainRate = 0.28f - nitroStat * 0.08f
            r.nitroFuel = (r.nitroFuel - drainRate * dt).coerceAtLeast(0f)
        } else {
            val recharge = if (r.isDrifting) 0.14f else 0.045f
            r.nitroFuel = (r.nitroFuel + recharge * dt).coerceAtMost(1f)
        }

        val targetMax = if (usingNitro) maxSpeed * (1.20f + nitroStat * 0.08f) else maxSpeed
        var accel = 0f
        if (input.throttle > 0.05f) {
            val speedFactor = (1f - (r.speed / targetMax) * 0.65f).coerceAtLeast(0.18f)
            accel += input.throttle * accelForce * speedFactor
        }
        if (usingNitro) {
            accel += 18f + nitroStat * 8f
        }
        if (input.brake > 0.05f) {
            if (r.speed > 2f) {
                accel -= input.brake * 32f
            } else {
                accel -= input.brake * 12f
            }
        }
        // Natural aerodynamic drag & rolling resistance
        if (input.throttle <= 0.05f && !usingNitro) {
            accel -= r.speed * 0.42f + 1.8f * kotlin.math.sign(r.speed)
        }

        val prevSpeed = r.speed
        r.speed = (r.speed + accel * dt).coerceIn(-14f, targetMax)

        // Steering & controlled drift
        val speedNorm = (abs(r.speed) / 30f).coerceIn(0f, 1.15f)
        val driftBoost = if (input.brake > 0.25f && abs(input.steer) > 0.3f && r.speed > 18f) 1.32f else 1.0f
        r.isDrifting = driftBoost > 1.1f
        val yawDelta = -input.steer * turnRate * speedNorm * driftBoost * dt * (if (r.speed >= -0.5f) 1f else -1f)
        r.yawRad += yawDelta
        r.steerVisual = lerpFloat(r.steerVisual, -input.steer * 0.42f, (dt * 12f).coerceAtMost(1f))

        // Suspension pitch & roll
        val longAccel = (r.speed - prevSpeed) / dt.coerceAtLeast(0.001f)
        val targetPitch = (-longAccel * 0.0018f).coerceIn(-0.05f, 0.05f)
        val targetRoll = (input.steer * speedNorm * 0.065f).coerceIn(-0.07f, 0.07f)
        r.pitchRad = lerpFloat(r.pitchRad, targetPitch, (dt * 9f).coerceAtMost(1f))
        r.rollRad = lerpFloat(r.rollRad, targetRoll, (dt * 9f).coerceAtMost(1f))

        moveAlongHeading(r, dt)
    }

    private fun stepAi(r: RacerEntity, dt: Float) {
        val samples = builtTrack.samples
        val lookAheadIdx = (r.closestSampleIdx + 7) % samples.size
        val targetSample = samples[lookAheadIdx]

        // Rubber-banding subtly keeps pack exciting
        val progressDiff = player.totalProgress - r.totalProgress
        val rubberBand = (1f + progressDiff * 0.22f).coerceIn(0.86f, 1.14f)

        val targetPos = targetSample.pos + targetSample.right * r.aiTargetLaneOffset
        val dx = targetPos.x - r.x
        val dz = targetPos.z - r.z
        val desiredYaw = atan2(dx, dz)

        var angleDiff = desiredYaw - r.yawRad
        while (angleDiff > Math.PI) angleDiff -= (2.0 * Math.PI).toFloat()
        while (angleDiff < -Math.PI) angleDiff += (2.0 * Math.PI).toFloat()

        val steerCmd = (angleDiff * 2.2f).coerceIn(-1f, 1f)
        r.yawRad += steerCmd * 2.1f * dt
        r.steerVisual = lerpFloat(r.steerVisual, steerCmd * 0.35f, (dt * 10f).coerceAtMost(1f))

        val cornerSlowdown = (1f - abs(angleDiff) * 0.45f).coerceIn(0.62f, 1f)
        val topSpeed = (50f + (r.spec.baseTopSpeed / 100f) * 20f) * r.aiBaseSkill * rubberBand * cornerSlowdown
        r.speed = lerpFloat(r.speed, topSpeed, (dt * 1.4f).coerceAtMost(1f))
        r.isNitroActive = abs(angleDiff) < 0.08f && rubberBand > 1.04f

        moveAlongHeading(r, dt)
    }

    private fun moveAlongHeading(r: RacerEntity, dt: Float) {
        val fwdX = sin(r.yawRad)
        val fwdZ = cos(r.yawRad)
        r.x += fwdX * r.speed * dt
        r.z += fwdZ * r.speed * dt
        r.wheelSpinRad = (r.wheelSpinRad + (r.speed / 0.35f) * dt) % (2f * Math.PI.toFloat())
    }

    private fun updateTrackProgress(r: RacerEntity) {
        val samples = builtTrack.samples
        val idx = builtTrack.findClosestSampleIndex(r.x, r.z, r.closestSampleIdx)
        r.closestSampleIdx = idx
        val s = samples[idx]

        // Smooth vertical terrain height alignment
        r.y = s.pos.y

        // Lateral guardrail containment
        val rel = Vec3(r.x - s.pos.x, 0f, r.z - s.pos.z)
        val lat = rel.dot(s.right)
        val maxLat = builtTrack.roadHalfWidth - 0.6f
        if (abs(lat) > maxLat) {
            val clampedLat = maxLat * kotlin.math.sign(lat)
            val corrected = s.pos + s.right * clampedLat
            r.x = corrected.x
            r.z = corrected.z
            r.speed *= 0.88f
            // Gently align heading back toward track tangent
            val trackYaw = atan2(s.tangent.x, s.tangent.z)
            var diff = trackYaw - r.yawRad
            while (diff > Math.PI) diff -= (2.0 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2.0 * Math.PI).toFloat()
            r.yawRad += diff * 0.25f
        }

        val currentT = s.t
        // Detect lap crossing forward across 0.9 -> 0.1
        if (r.lastT > 0.85f && currentT < 0.15f) {
            val lapDuration = raceElapsedMs - r.lapStartMs
            if (r.lap >= 1 && lapDuration > 4000L && lapDuration < r.bestLapMs) {
                r.bestLapMs = lapDuration
            }
            r.lapStartMs = raceElapsedMs
            if (r.lap >= trackSpec.laps && !r.finished) {
                r.finished = true
                r.finishTimeMs = raceElapsedMs
            } else if (!r.finished) {
                r.lap += 1
            }
        } else if (r.lastT < 0.15f && currentT > 0.85f && r.lap > 1) {
            // Went backwards across start line
            r.lap -= 1
        }
        r.lastT = currentT
        r.totalProgress = (r.lap - 1) + currentT
    }

    private fun resolveCarCollisions() {
        val minDist = 2.3f
        val minDistSq = minDist * minDist
        for (i in racers.indices) {
            for (j in i + 1 until racers.size) {
                val a = racers[i]
                val b = racers[j]
                val dx = b.x - a.x
                val dz = b.z - a.z
                val dSq = dx * dx + dz * dz
                if (dSq in 0.0001f..minDistSq) {
                    val d = sqrt(dSq)
                    val push = (minDist - d) * 0.5f
                    val nx = dx / d
                    val nz = dz / d
                    a.x -= nx * push
                    a.z -= nz * push
                    b.x += nx * push
                    b.z += nz * push
                    a.speed *= 0.96f
                    b.speed *= 0.96f
                }
            }
        }
    }

    fun buildTelemetry(): RaceTelemetry {
        val p = player
        val sorted = racers.sortedByDescending { it.totalProgress }
        val pos = (sorted.indexOf(p) + 1).coerceIn(1, racers.size)

        val kmh = (abs(p.speed) * 3.6f).toInt()
        val gear = when {
            kmh < 40 -> 1
            kmh < 85 -> 2
            kmh < 135 -> 3
            kmh < 185 -> 4
            kmh < 235 -> 5
            else -> 6
        }
        val rpmNorm = when (gear) {
            1 -> (kmh / 45f).coerceIn(0.18f, 0.95f)
            2 -> ((kmh - 25) / 65f).coerceIn(0.30f, 0.96f)
            3 -> ((kmh - 65) / 75f).coerceIn(0.35f, 0.97f)
            4 -> ((kmh - 110) / 80f).coerceIn(0.40f, 0.98f)
            5 -> ((kmh - 155) / 85f).coerceIn(0.42f, 0.98f)
            else -> ((kmh - 200) / 95f).coerceIn(0.45f, 1.0f)
        }

        val currentLapElapsed = if (raceStarted) (raceElapsedMs - p.lapStartMs).coerceAtLeast(0L) else 0L
        val bestLap = if (p.bestLapMs == Long.MAX_VALUE) currentLapElapsed else p.bestLapMs

        val dots = racers.map { r ->
            MinimapCarDot(
                normX = (r.x - minX) / span,
                normZ = (r.z - minZ) / span,
                isPlayer = r.isPlayer
            )
        }

        return RaceTelemetry(
            speedKmh = kmh,
            rpmNorm = rpmNorm,
            gear = gear,
            nitroNorm = p.nitroFuel,
            currentLap = p.lap.coerceAtMost(trackSpec.laps),
            totalLaps = trackSpec.laps,
            position = pos,
            currentLapMs = currentLapElapsed,
            bestLapMs = bestLap,
            totalTimeMs = if (p.finished) p.finishTimeMs else raceElapsedMs,
            countdownSec = if (raceStarted) 0 else kotlin.math.ceil(countdownTimerSec).toInt().coerceIn(1, 3),
            isFinished = p.finished,
            isDrifting = p.isDrifting,
            isNitroActive = p.isNitroActive,
            cameraMode = cameraMode,
            minimapTrack = builtTrack.minimapPoints,
            minimapCars = dots
        )
    }
}
