package com.example.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PaintFinish(val labelFa: String, val roughness: Float, val metallic: Float, val pearl: Float) {
    GLOSS("براق (Gloss)", 0.14f, 0.25f, 0.0f),
    METALLIC("متالیک (Metallic)", 0.20f, 0.85f, 0.0f),
    MATTE("مات (Matte)", 0.72f, 0.10f, 0.0f),
    PEARL("صدفی (Pearlescent)", 0.12f, 0.55f, 1.0f);

    companion object {
        fun fromRaw(raw: String?): PaintFinish =
            entries.firstOrNull { it.name == raw } ?: METALLIC
    }
}

enum class RimStyle(val labelFa: String, val spokeCount: Int, val dishDepth: Float) {
    SPORT_5("اسپرت ۵ پره", 5, 0.08f),
    TURBINE_10("توربین ۱۰ پره", 10, 0.10f),
    DEEP_DISH("دیپ‌دیش دریفت", 6, 0.20f),
    CARBON_AERO("آیرودینامیک کربن", 12, 0.04f);

    companion object {
        fun fromRaw(raw: String?): RimStyle =
            entries.firstOrNull { it.name == raw } ?: SPORT_5
    }
}

enum class SpoilerStyle(val labelFa: String) {
    NONE("بدون اسپویلر"),
    LIP("لبه‌ای کربن"),
    GT_WING("باله GT مسابقه‌ای"),
    SWAN_NECK("باله Swan-Neck");

    companion object {
        fun fromRaw(raw: String?): SpoilerStyle =
            entries.firstOrNull { it.name == raw } ?: LIP
    }
}

enum class CarBodyArchetype {
    SPORT_COUPE,
    MID_SUPERCAR,
    MUSCLE_GT,
    JDM_DRIFT,
    PROTOTYPE_LM,
    ROADSTER_V12
}

data class CarSpec(
    val id: String,
    val name: String,
    val classLabelFa: String,
    val descriptionFa: String,
    val price: Long,
    val archetype: CarBodyArchetype,
    val defaultPaintHex: String,
    val baseTopSpeed: Int,      // out of 100
    val baseAcceleration: Int,  // out of 100
    val baseHandling: Int,      // out of 100
    val baseNitro: Int,         // out of 100
    val widthScale: Float,
    val heightScale: Float,
    val lengthScale: Float
)

data class TrackSpec(
    val id: String,
    val nameFa: String,
    val subtitleFa: String,
    val laps: Int,
    val rewardBase: Long,
    val difficultyLabelFa: String,
    val aiSpeedFactor: Float,
    @DrawableRes val thumbnailRes: Int,
    val isNight: Boolean,
    val skyTopRgb: FloatArray,
    val skyBottomRgb: FloatArray,
    val roadRgb: FloatArray,
    val curbPrimaryRgb: FloatArray,
    val curbSecondaryRgb: FloatArray,
    val groundRgb: FloatArray,
    val accentRgb: FloatArray
)

data class ColorSwatch(
    val nameFa: String,
    val hex: String
)

object RacingCatalog {
    val cars: List<CarSpec> = listOf(
        CarSpec(
            id = "viper_gtr",
            name = "Viper GT-R",
            classLabelFa = "کوپه اسپرت",
            descriptionFa = "تعادل بی‌نظیر بین شتاب، کنترل در پیچ‌ها و پایداری سرعت بالا.",
            price = 0L,
            archetype = CarBodyArchetype.SPORT_COUPE,
            defaultPaintHex = "#FF2E4C",
            baseTopSpeed = 72,
            baseAcceleration = 70,
            baseHandling = 76,
            baseNitro = 68,
            widthScale = 1.0f,
            heightScale = 1.0f,
            lengthScale = 1.0f
        ),
        CarSpec(
            id = "cyber_drift",
            name = "Cyber Drift S15",
            classLabelFa = "دریفت خیابانی",
            descriptionFa = "کیت بدنه پهن JDM با هندلینگ تیز برای دریفت‌های سریع و فنی.",
            price = 12000L,
            archetype = CarBodyArchetype.JDM_DRIFT,
            defaultPaintHex = "#00E5FF",
            baseTopSpeed = 76,
            baseAcceleration = 78,
            baseHandling = 88,
            baseNitro = 75,
            widthScale = 1.04f,
            heightScale = 0.96f,
            lengthScale = 1.0f
        ),
        CarSpec(
            id = "titan_muscle",
            name = "Titan Muscle GT",
            classLabelFa = "ماسکل V8",
            descriptionFa = "موتور ۸ سیلندر قدرتمند با گشتاور وحشی در مسیرهای مستقیم.",
            price = 18000L,
            archetype = CarBodyArchetype.MUSCLE_GT,
            defaultPaintHex = "#FFB800",
            baseTopSpeed = 84,
            baseAcceleration = 82,
            baseHandling = 68,
            baseNitro = 84,
            widthScale = 1.08f,
            heightScale = 1.04f,
            lengthScale = 1.05f
        ),
        CarSpec(
            id = "apex_phantom",
            name = "Apex Phantom V10",
            classLabelFa = "سوپراسپرت موتوروسط",
            descriptionFa = "طراحی گوه‌ای آیرودینامیک با چسبندگی فوق‌العاده در سرعت‌های بالا.",
            price = 32000L,
            archetype = CarBodyArchetype.MID_SUPERCAR,
            defaultPaintHex = "#9D4EDD",
            baseTopSpeed = 89,
            baseAcceleration = 86,
            baseHandling = 85,
            baseNitro = 82,
            widthScale = 1.06f,
            heightScale = 0.90f,
            lengthScale = 1.03f
        ),
        CarSpec(
            id = "solaris_v12",
            name = "Solaris Roadster",
            classLabelFa = "رودستر اگزوتیک",
            descriptionFa = "بدنه سبک کربنی با کابین روباز و شتاب انفجاری در خروج از پیچ.",
            price = 48000L,
            archetype = CarBodyArchetype.ROADSTER_V12,
            defaultPaintHex = "#FF6B2B",
            baseTopSpeed = 92,
            baseAcceleration = 91,
            baseHandling = 88,
            baseNitro = 88,
            widthScale = 1.05f,
            heightScale = 0.86f,
            lengthScale = 1.02f
        ),
        CarSpec(
            id = "hyperion_proto",
            name = "Hyperion LM-1",
            classLabelFa = "هایپرکار پروتوتایپ",
            descriptionFa = "نهایت مهندسی پیست با باله مرکزی، داون‌فورس عظیم و سقف سرعت استثنایی.",
            price = 75000L,
            archetype = CarBodyArchetype.PROTOTYPE_LM,
            defaultPaintHex = "#F5F7FA",
            baseTopSpeed = 98,
            baseAcceleration = 95,
            baseHandling = 94,
            baseNitro = 92,
            widthScale = 1.10f,
            heightScale = 0.84f,
            lengthScale = 1.08f
        )
    )

    val tracks: List<TrackSpec> = listOf(
        TrackSpec(
            id = "neon_tokyo",
            nameFa = "نئون توکیو",
            subtitleFa = "پیست شبانه شهری با آسفالت مرطوب و نورپردازی نئونی",
            laps = 3,
            rewardBase = 5000L,
            difficultyLabelFa = "متوسط",
            aiSpeedFactor = 0.92f,
            thumbnailRes = R.drawable.img_track_neon,
            isNight = true,
            skyTopRgb = floatArrayOf(0.03f, 0.04f, 0.11f),
            skyBottomRgb = floatArrayOf(0.11f, 0.05f, 0.22f),
            roadRgb = floatArrayOf(0.09f, 0.10f, 0.13f),
            curbPrimaryRgb = floatArrayOf(0.0f, 0.90f, 1.0f),
            curbSecondaryRgb = floatArrayOf(1.0f, 0.18f, 0.45f),
            groundRgb = floatArrayOf(0.04f, 0.05f, 0.09f),
            accentRgb = floatArrayOf(0.0f, 0.90f, 1.0f)
        ),
        TrackSpec(
            id = "alpine_pass",
            nameFa = "گذرگاه آلپ",
            subtitleFa = "مسیر کوهستانی غروب با پیچ‌های تند و ارتفاع متغیر",
            laps = 3,
            rewardBase = 7000L,
            difficultyLabelFa = "حرفه‌ای",
            aiSpeedFactor = 0.98f,
            thumbnailRes = R.drawable.img_track_alpine,
            isNight = false,
            skyTopRgb = floatArrayOf(0.16f, 0.28f, 0.55f),
            skyBottomRgb = floatArrayOf(0.95f, 0.54f, 0.30f),
            roadRgb = floatArrayOf(0.16f, 0.17f, 0.19f),
            curbPrimaryRgb = floatArrayOf(0.95f, 0.18f, 0.22f),
            curbSecondaryRgb = floatArrayOf(0.95f, 0.95f, 0.95f),
            groundRgb = floatArrayOf(0.12f, 0.24f, 0.14f),
            accentRgb = floatArrayOf(1.0f, 0.72f, 0.0f)
        ),
        TrackSpec(
            id = "desert_canyon",
            nameFa = "دره سرخ",
            subtitleFa = "بزرگراه کویری پرسرعت در میان صخره‌های سرخ‌فام",
            laps = 3,
            rewardBase = 9500L,
            difficultyLabelFa = "تخصصی",
            aiSpeedFactor = 1.04f,
            thumbnailRes = R.drawable.img_track_desert,
            isNight = false,
            skyTopRgb = floatArrayOf(0.22f, 0.52f, 0.85f),
            skyBottomRgb = floatArrayOf(0.96f, 0.74f, 0.48f),
            roadRgb = floatArrayOf(0.18f, 0.17f, 0.16f),
            curbPrimaryRgb = floatArrayOf(1.0f, 0.65f, 0.0f),
            curbSecondaryRgb = floatArrayOf(0.15f, 0.15f, 0.15f),
            groundRgb = floatArrayOf(0.62f, 0.33f, 0.19f),
            accentRgb = floatArrayOf(1.0f, 0.45f, 0.15f)
        )
    )

    val paintPalette: List<ColorSwatch> = listOf(
        ColorSwatch("قرمز آتشین", "#FF2E4C"),
        ColorSwatch("آبی کوبالت", "#1E88FF"),
        ColorSwatch("زرد خورشیدی", "#FFB800"),
        ColorSwatch("فیروزه‌ای سایبری", "#00E5FF"),
        ColorSwatch("سبز زمردی", "#00E676"),
        ColorSwatch("بنفش نئون", "#9D4EDD"),
        ColorSwatch("سفید صدفی", "#F5F7FA"),
        ColorSwatch("مشکی کربن", "#1A1E28"),
        ColorSwatch("نارنجی مسابقه‌ای", "#FF6B2B"),
        ColorSwatch("خاکستری تیتانیوم", "#64748B")
    )

    val rimPalette: List<ColorSwatch> = listOf(
        ColorSwatch("نقره کروم", "#D9DFE8"),
        ColorSwatch("مشکی مات", "#1B202B"),
        ColorSwatch("طلایی برنز", "#E6B33D"),
        ColorSwatch("قرمز مسابقه‌ای", "#FF2E4C"),
        ColorSwatch("آبی نئون", "#00E5FF")
    )

    val underglowPalette: List<ColorSwatch> = listOf(
        ColorSwatch("خاموش", "NONE"),
        ColorSwatch("فیروزه‌ای", "#00E5FF"),
        ColorSwatch("قرمز", "#FF2E4C"),
        ColorSwatch("طلایی", "#FFB800"),
        ColorSwatch("زمردی", "#00E676"),
        ColorSwatch("بنفش", "#B517FF")
    )

    fun carById(id: String): CarSpec = cars.firstOrNull { it.id == id } ?: cars.first()
    fun trackById(id: String): TrackSpec = tracks.firstOrNull { it.id == id } ?: tracks.first()

    fun upgradeCost(currentLevel: Int): Long = when (currentLevel) {
        1 -> 2500L
        2 -> 5000L
        3 -> 9000L
        4 -> 15000L
        else -> 0L
    }
}

@Keep
@IgnoreExtraProperties
data class PlayerProfile(
    val userId: String = "",
    val displayName: String = "راننده اپکس",
    val credits: Long = 25000L,
    val selectedCarId: String = "viper_gtr",
    val totalRaces: Long = 0L,
    val totalWins: Long = 0L,
    val bestLapNeonMs: Long? = null,
    val bestLapAlpineMs: Long? = null,
    val bestLapDesertMs: Long? = null,
    val unlockedCarIds: List<String> = listOf("viper_gtr"),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CarSetup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val carId: String = "viper_gtr",
    val paintColorHex: String = "#FF2E4C",
    val paintFinish: String = PaintFinish.METALLIC.name,
    val rimStyle: String = RimStyle.SPORT_5.name,
    val rimColorHex: String = "#D9DFE8",
    val spoilerStyle: String = SpoilerStyle.LIP.name,
    val underglowHex: String = "NONE",
    val engineLevel: Long = 1L,
    val turboLevel: Long = 1L,
    val tiresLevel: Long = 1L,
    val nitroLevel: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val finishEnum: PaintFinish get() = PaintFinish.fromRaw(paintFinish)
    val rimEnum: RimStyle get() = RimStyle.fromRaw(rimStyle)
    val spoilerEnum: SpoilerStyle get() = SpoilerStyle.fromRaw(spoilerStyle)

    fun effectiveTopSpeed(spec: CarSpec): Int =
        (spec.baseTopSpeed + (engineLevel.toInt() - 1) * 4).coerceAtMost(100)

    fun effectiveAcceleration(spec: CarSpec): Int =
        (spec.baseAcceleration + (turboLevel.toInt() - 1) * 4).coerceAtMost(100)

    fun effectiveHandling(spec: CarSpec): Int =
        (spec.baseHandling + (tiresLevel.toInt() - 1) * 4).coerceAtMost(100)

    fun effectiveNitro(spec: CarSpec): Int =
        (spec.baseNitro + (nitroLevel.toInt() - 1) * 4).coerceAtMost(100)

    companion object {
        fun defaultFor(car: CarSpec, userId: String = ""): CarSetup = CarSetup(
            id = car.id,
            userId = userId,
            carId = car.id,
            paintColorHex = car.defaultPaintHex,
            paintFinish = PaintFinish.METALLIC.name,
            rimStyle = RimStyle.SPORT_5.name,
            rimColorHex = "#D9DFE8",
            spoilerStyle = if (car.archetype == CarBodyArchetype.PROTOTYPE_LM) SpoilerStyle.SWAN_NECK.name else SpoilerStyle.LIP.name,
            underglowHex = "NONE",
            engineLevel = 1L,
            turboLevel = 1L,
            tiresLevel = 1L,
            nitroLevel = 1L
        )
    }
}

@Keep
@IgnoreExtraProperties
data class RaceResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackId: String = "neon_tokyo",
    val carId: String = "viper_gtr",
    val position: Long = 1L,
    val totalTimeMs: Long = 0L,
    val bestLapMs: Long = 0L,
    val creditsEarned: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun hexToRgbFloats(hex: String, fallback: FloatArray = floatArrayOf(1f, 0.18f, 0.30f)): FloatArray {
    if (!hex.startsWith("#") || hex.length != 7) return fallback
    return try {
        val r = hex.substring(1, 3).toInt(16) / 255f
        val g = hex.substring(3, 5).toInt(16) / 255f
        val b = hex.substring(5, 7).toInt(16) / 255f
        floatArrayOf(r, g, b)
    } catch (_: Exception) {
        fallback
    }
}

fun formatTimeMs(ms: Long?): String {
    if (ms == null || ms <= 0L) return "--:--.--"
    val totalHundredths = ms / 10L
    val hundredths = totalHundredths % 100L
    val totalSeconds = totalHundredths / 100L
    val seconds = totalSeconds % 60L
    val minutes = totalSeconds / 60L
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}
