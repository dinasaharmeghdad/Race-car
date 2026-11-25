package com.example

import com.example.engine3d.Vec3
import com.example.engine3d.catmullRomClosed
import com.example.model.CarSetup
import com.example.model.PaintFinish
import com.example.model.RacingCatalog
import com.example.model.RimStyle
import com.example.model.SpoilerStyle
import com.example.model.formatTimeMs
import com.example.model.hexToRgbFloats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingEngineTest {

    @Test
    fun catalogHasSixDiverseCarsAndThreeTracks() {
        assertEquals(6, RacingCatalog.cars.size)
        assertEquals(3, RacingCatalog.tracks.size)
        assertEquals(0L, RacingCatalog.carById("viper_gtr").price)
    }

    @Test
    fun carSetupEffectiveStatsScaleWithUpgradesAndClampAt100() {
        val spec = RacingCatalog.carById("hyperion_proto")
        val maxed = CarSetup(
            carId = spec.id,
            engineLevel = 5L,
            turboLevel = 5L,
            tiresLevel = 5L,
            nitroLevel = 5L
        )
        assertEquals(100, maxed.effectiveTopSpeed(spec))
        assertEquals(100, maxed.effectiveAcceleration(spec))
        assertEquals(100, maxed.effectiveHandling(spec))
        assertEquals(100, maxed.effectiveNitro(spec))
    }

    @Test
    fun enumDeserializationFallsBackSafely() {
        assertEquals(PaintFinish.METALLIC, PaintFinish.fromRaw("UNKNOWN_FINISH"))
        assertEquals(RimStyle.SPORT_5, RimStyle.fromRaw(null))
        assertEquals(SpoilerStyle.LIP, SpoilerStyle.fromRaw("INVALID"))
    }

    @Test
    fun hexColorAndTimeFormattingWork() {
        val rgb = hexToRgbFloats("#FF0080")
        assertEquals(1.0f, rgb[0], 0.01f)
        assertEquals(0.0f, rgb[1], 0.01f)
        assertEquals(128f / 255f, rgb[2], 0.01f)
        assertEquals("01:05.42", formatTimeMs(65420L))
        assertEquals("--:--.--", formatTimeMs(null))
    }

    @Test
    fun catmullRomClosedProducesContinuousLoop() {
        val pts = listOf(
            Vec3(0f, 0f, 0f),
            Vec3(100f, 0f, 0f),
            Vec3(100f, 0f, 100f),
            Vec3(0f, 0f, 100f)
        )
        val pStart = catmullRomClosed(pts, 0f)
        val pAlmostEnd = catmullRomClosed(pts, 0.999f)
        assertTrue((pStart - pAlmostEnd).length() < 1.5f)
    }
}
