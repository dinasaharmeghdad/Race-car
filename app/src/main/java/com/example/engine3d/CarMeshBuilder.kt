package com.example.engine3d

import com.example.model.CarBodyArchetype
import com.example.model.CarSetup
import com.example.model.CarSpec
import com.example.model.RimStyle
import com.example.model.SpoilerStyle
import com.example.model.hexToRgbFloats
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BuiltCarMeshes(
    val bodyMesh: MeshGpuBuffer,
    val trimMesh: MeshGpuBuffer,
    val spoilerMesh: MeshGpuBuffer,
    val wheelMesh: MeshGpuBuffer,
    val underglowMesh: MeshGpuBuffer?,
    val wheelPositions: List<Vec3>
) {
    fun release() {
        bodyMesh.release()
        trimMesh.release()
        spoilerMesh.release()
        wheelMesh.release()
        underglowMesh?.release()
    }
}

object CarMeshBuilder {

    private data class CrossSection(
        val z: Float,
        val halfW: Float,
        val bottomY: Float,
        val beltY: Float,
        val roofY: Float,
        val roofHalfW: Float
    )

    fun buildCarMeshes(spec: CarSpec, setup: CarSetup): BuiltCarMeshes {
        val paintRgb = hexToRgbFloats(setup.paintColorHex)
        val rimRgb = hexToRgbFloats(setup.rimColorHex, floatArrayOf(0.85f, 0.88f, 0.92f))
        val ws = spec.widthScale
        val hs = spec.heightScale
        val ls = spec.lengthScale

        val bodyBuilder = MeshDataBuilder(6000)
        val trimBuilder = MeshDataBuilder(4000)
        val spoilerBuilder = MeshDataBuilder(1500)

        // Build sculpted aerodynamic fuselage via lofted cross-sections
        val sections = getSectionsForArchetype(spec.archetype).map { s ->
            CrossSection(
                z = s.z * ls,
                halfW = s.halfW * ws,
                bottomY = s.bottomY,
                beltY = s.beltY * hs,
                roofY = s.roofY * hs,
                roofHalfW = s.roofHalfW * ws
            )
        }

        val glassRgb = floatArrayOf(0.06f, 0.09f, 0.14f)
        val carbonRgb = floatArrayOf(0.11f, 0.12f, 0.15f)
        val grilleRgb = floatArrayOf(0.05f, 0.05f, 0.07f)
        val headlightRgb = floatArrayOf(0.95f, 0.98f, 1.0f)
        val taillightRgb = floatArrayOf(1.0f, 0.08f, 0.15f)
        val chromeRgb = floatArrayOf(0.82f, 0.85f, 0.90f)

        // Loft body shell & cabin glass
        for (i in 0 until sections.size - 1) {
            val a = sections[i]
            val b = sections[i + 1]

            // Lower side skirts & doors (painted)
            bodyBuilder.addQuad(
                Vec3(a.halfW, a.bottomY, a.z),
                Vec3(b.halfW, b.bottomY, b.z),
                Vec3(b.halfW * 0.96f, b.beltY, b.z),
                Vec3(a.halfW * 0.96f, a.beltY, a.z),
                paintRgb
            )
            bodyBuilder.addQuad(
                Vec3(-b.halfW, b.bottomY, b.z),
                Vec3(-a.halfW, a.bottomY, a.z),
                Vec3(-a.halfW * 0.96f, a.beltY, a.z),
                Vec3(-b.halfW * 0.96f, b.beltY, b.z),
                paintRgb
            )

            // Underbody floor pan
            trimBuilder.addQuad(
                Vec3(-a.halfW, a.bottomY, a.z),
                Vec3(-b.halfW, b.bottomY, b.z),
                Vec3(b.halfW, b.bottomY, b.z),
                Vec3(a.halfW, a.bottomY, a.z),
                carbonRgb
            )

            // Upper deck / cabin greenhouse
            val isCabinSection = (a.roofY - a.beltY > 0.18f) || (b.roofY - b.beltY > 0.18f)
            val isWindshieldOrRearGlass = isCabinSection && (i == 2 || i == 4 || i == 3)
            val topTarget = if (isWindshieldOrRearGlass && i != 3) trimBuilder else bodyBuilder
            val topColor = if (isWindshieldOrRearGlass && i != 3) glassRgb else paintRgb

            // Roof / Hood / Trunk center surface
            topTarget.addQuad(
                Vec3(-a.roofHalfW, a.roofY, a.z),
                Vec3(-b.roofHalfW, b.roofY, b.z),
                Vec3(b.roofHalfW, b.roofY, b.z),
                Vec3(a.roofHalfW, a.roofY, a.z),
                topColor
            )

            // Side windows / shoulder slopes
            val shoulderTarget = if (isCabinSection) trimBuilder else bodyBuilder
            val shoulderColor = if (isCabinSection) glassRgb else paintRgb
            shoulderTarget.addQuad(
                Vec3(a.halfW * 0.96f, a.beltY, a.z),
                Vec3(b.halfW * 0.96f, b.beltY, b.z),
                Vec3(b.roofHalfW, b.roofY, b.z),
                Vec3(a.roofHalfW, a.roofY, a.z),
                shoulderColor
            )
            shoulderTarget.addQuad(
                Vec3(-b.halfW * 0.96f, b.beltY, b.z),
                Vec3(-a.halfW * 0.96f, a.beltY, a.z),
                Vec3(-a.roofHalfW, a.roofY, a.z),
                Vec3(-b.roofHalfW, b.roofY, b.z),
                shoulderColor
            )
        }

        // Front nose cap & Rear fascia cap
        val front = sections.first()
        val rear = sections.last()
        bodyBuilder.addQuad(
            Vec3(-front.halfW, front.bottomY, front.z),
            Vec3(front.halfW, front.bottomY, front.z),
            Vec3(front.halfW * 0.96f, front.beltY, front.z),
            Vec3(-front.halfW * 0.96f, front.beltY, front.z),
            paintRgb
        )
        bodyBuilder.addQuad(
            Vec3(-front.halfW * 0.96f, front.beltY, front.z),
            Vec3(front.halfW * 0.96f, front.beltY, front.z),
            Vec3(front.roofHalfW, front.roofY, front.z),
            Vec3(-front.roofHalfW, front.roofY, front.z),
            paintRgb
        )
        bodyBuilder.addQuad(
            Vec3(rear.halfW, rear.bottomY, rear.z),
            Vec3(-rear.halfW, rear.bottomY, rear.z),
            Vec3(-rear.halfW * 0.96f, rear.beltY, rear.z),
            Vec3(rear.halfW * 0.96f, rear.beltY, rear.z),
            paintRgb
        )
        bodyBuilder.addQuad(
            Vec3(rear.halfW * 0.96f, rear.beltY, rear.z),
            Vec3(-rear.halfW * 0.96f, rear.beltY, rear.z),
            Vec3(-rear.roofHalfW, rear.roofY, rear.z),
            Vec3(rear.roofHalfW, rear.roofY, rear.z),
            paintRgb
        )

        // Sculpted Fender Flares / Side Skirts / Splitters / Diffusers
        val frontZ = front.z
        val rearZ = rear.z
        // Front Carbon Splitter
        trimBuilder.addBox(0f, front.bottomY - 0.02f, frontZ + 0.07f, front.halfW * 0.98f, 0.03f, 0.14f, carbonRgb)
        // Front Intake Grille
        trimBuilder.addBox(0f, front.bottomY + 0.11f, frontZ + 0.01f, front.halfW * 0.52f, 0.09f, 0.03f, grilleRgb)
        // Side Carbon Skirts
        trimBuilder.addBox(front.halfW * 0.99f, front.bottomY + 0.01f, 0f, 0.05f, 0.04f, 1.45f * ls, carbonRgb)
        trimBuilder.addBox(-front.halfW * 0.99f, front.bottomY + 0.01f, 0f, 0.05f, 0.04f, 1.45f * ls, carbonRgb)
        // Rear Diffuser + Fins
        trimBuilder.addBox(0f, rear.bottomY + 0.04f, rearZ - 0.03f, rear.halfW * 0.85f, 0.09f, 0.08f, carbonRgb)
        for (finX in listOf(-0.45f, -0.15f, 0.15f, 0.45f)) {
            trimBuilder.addBox(finX * ws, rear.bottomY + 0.04f, rearZ - 0.07f, 0.02f, 0.08f, 0.10f, carbonRgb)
        }

        // Emissive LED Headlights
        val hlY = front.beltY - 0.06f
        trimBuilder.addBox(front.halfW * 0.68f, hlY, frontZ + 0.015f, 0.21f * ws, 0.05f, 0.04f, headlightRgb, emissive = 1.0f)
        trimBuilder.addBox(-front.halfW * 0.68f, hlY, frontZ + 0.015f, 0.21f * ws, 0.05f, 0.04f, headlightRgb, emissive = 1.0f)

        // Emissive LED Taillights (full-width blade or twin blades)
        val tlY = rear.beltY - 0.06f
        trimBuilder.addBox(rear.halfW * 0.65f, tlY, rearZ - 0.015f, 0.24f * ws, 0.055f, 0.04f, taillightRgb, emissive = 1.0f)
        trimBuilder.addBox(-rear.halfW * 0.65f, tlY, rearZ - 0.015f, 0.24f * ws, 0.055f, 0.04f, taillightRgb, emissive = 1.0f)
        if (spec.archetype == CarBodyArchetype.MID_SUPERCAR || spec.archetype == CarBodyArchetype.PROTOTYPE_LM) {
            trimBuilder.addBox(0f, tlY, rearZ - 0.015f, rear.halfW * 0.40f, 0.025f, 0.035f, taillightRgb, emissive = 1.0f)
        }

        // Dual Chrome Exhaust Pipes
        for (exX in listOf(-0.48f * ws, -0.34f * ws, 0.34f * ws, 0.48f * ws)) {
            trimBuilder.addBox(exX, rear.bottomY + 0.08f, rearZ - 0.06f, 0.045f, 0.045f, 0.10f, chromeRgb)
        }

        // Side Mirrors
        val mirrorY = sections[2].beltY + 0.08f
        val mirrorZ = sections[2].z - 0.05f
        bodyBuilder.addBox(ws * 1.04f, mirrorY, mirrorZ, 0.10f, 0.045f, 0.08f, paintRgb)
        bodyBuilder.addBox(-ws * 1.04f, mirrorY, mirrorZ, 0.10f, 0.045f, 0.08f, paintRgb)

        // Archetype-specific signature details
        when (spec.archetype) {
            CarBodyArchetype.MUSCLE_GT -> {
                // Raised hood power-dome & racing stripes
                trimBuilder.addBox(0f, sections[1].roofY + 0.045f, (sections[1].z + sections[2].z) * 0.5f, 0.28f * ws, 0.045f, 0.65f * ls, carbonRgb)
            }
            CarBodyArchetype.PROTOTYPE_LM -> {
                // Dorsal aero shark-fin on engine cover
                bodyBuilder.addBox(0f, sections[4].roofY + 0.14f, (sections[4].z + sections[5].z) * 0.5f, 0.025f, 0.18f, 0.55f * ls, paintRgb)
                // Roof scoop
                trimBuilder.addBox(0f, sections[3].roofY + 0.06f, sections[3].z, 0.18f, 0.06f, 0.25f, carbonRgb)
            }
            CarBodyArchetype.JDM_DRIFT -> {
                // Widebody overfender flares
                val fz = 1.32f * ls; val rz = -1.32f * ls
                bodyBuilder.addBox(ws * 1.02f, 0.42f, fz, 0.08f, 0.20f, 0.42f, paintRgb)
                bodyBuilder.addBox(-ws * 1.02f, 0.42f, fz, 0.08f, 0.20f, 0.42f, paintRgb)
                bodyBuilder.addBox(ws * 1.04f, 0.44f, rz, 0.09f, 0.22f, 0.46f, paintRgb)
                bodyBuilder.addBox(-ws * 1.04f, 0.44f, rz, 0.09f, 0.22f, 0.46f, paintRgb)
            }
            CarBodyArchetype.MID_SUPERCAR -> {
                // Side air intake blades
                trimBuilder.addBox(ws * 0.96f, 0.50f * hs, -0.45f * ls, 0.06f, 0.18f, 0.32f, carbonRgb)
                trimBuilder.addBox(-ws * 0.96f, 0.50f * hs, -0.45f * ls, 0.06f, 0.18f, 0.32f, carbonRgb)
            }
            CarBodyArchetype.ROADSTER_V12 -> {
                // Twin metallic roll hoops behind seats
                trimBuilder.addBox(0.32f * ws, sections[3].roofY + 0.06f, -0.25f * ls, 0.09f, 0.09f, 0.09f, chromeRgb)
                trimBuilder.addBox(-0.32f * ws, sections[3].roofY + 0.06f, -0.25f * ls, 0.09f, 0.09f, 0.09f, chromeRgb)
            }
            CarBodyArchetype.SPORT_COUPE -> {
                // Front hood vents
                trimBuilder.addBox(0.32f * ws, sections[1].roofY + 0.02f, sections[1].z - 0.10f, 0.14f, 0.02f, 0.22f, carbonRgb)
                trimBuilder.addBox(-0.32f * ws, sections[1].roofY + 0.02f, sections[1].z - 0.10f, 0.14f, 0.02f, 0.22f, carbonRgb)
            }
        }

        // Build Spoiler Mesh
        val deckY = rear.roofY
        val wingZ = rearZ + 0.16f
        when (setup.spoilerEnum) {
            SpoilerStyle.NONE -> Unit
            SpoilerStyle.LIP -> {
                spoilerBuilder.addBox(0f, deckY + 0.045f, rearZ + 0.05f, rear.halfW * 0.88f, 0.035f, 0.10f, carbonRgb)
            }
            SpoilerStyle.GT_WING -> {
                val wingY = deckY + 0.26f
                spoilerBuilder.addBox(0.45f * ws, deckY + 0.13f, wingZ, 0.03f, 0.14f, 0.12f, carbonRgb)
                spoilerBuilder.addBox(-0.45f * ws, deckY + 0.13f, wingZ, 0.03f, 0.14f, 0.12f, carbonRgb)
                spoilerBuilder.addBox(0f, wingY, wingZ - 0.03f, rear.halfW * 1.04f, 0.03f, 0.22f, paintRgb)
                spoilerBuilder.addBox(rear.halfW * 1.04f, wingY, wingZ - 0.03f, 0.025f, 0.11f, 0.25f, carbonRgb)
                spoilerBuilder.addBox(-rear.halfW * 1.04f, wingY, wingZ - 0.03f, 0.025f, 0.11f, 0.25f, carbonRgb)
            }
            SpoilerStyle.SWAN_NECK -> {
                val wingY = deckY + 0.34f
                spoilerBuilder.addBox(0.30f * ws, deckY + 0.20f, wingZ + 0.06f, 0.025f, 0.20f, 0.10f, carbonRgb)
                spoilerBuilder.addBox(-0.30f * ws, deckY + 0.20f, wingZ + 0.06f, 0.025f, 0.20f, 0.10f, carbonRgb)
                spoilerBuilder.addBox(0f, wingY, wingZ - 0.05f, rear.halfW * 1.12f, 0.03f, 0.26f, carbonRgb)
                spoilerBuilder.addBox(rear.halfW * 1.12f, wingY - 0.02f, wingZ - 0.05f, 0.025f, 0.14f, 0.30f, paintRgb)
                spoilerBuilder.addBox(-rear.halfW * 1.12f, wingY - 0.02f, wingZ - 0.05f, 0.025f, 0.14f, 0.30f, paintRgb)
            }
        }

        // Build Customizable 3D Wheel + Rim Mesh
        val wheelMesh = buildWheelMesh(setup.rimEnum, rimRgb)

        // Build Neon Underglow Halo Mesh (if enabled)
        val underglowMesh = if (setup.underglowHex != "NONE") {
            val glowRgb = hexToRgbFloats(setup.underglowHex, floatArrayOf(0f, 0.9f, 1f))
            val gb = MeshDataBuilder(128)
            val gw = 1.18f * ws
            val gl = 2.30f * ls
            gb.addQuad(
                Vec3(-gw, 0.025f, -gl),
                Vec3(-gw, 0.025f, gl),
                Vec3(gw, 0.025f, gl),
                Vec3(gw, 0.025f, -gl),
                glowRgb,
                emissive = 1.0f
            )
            gb.buildGpuBuffer()
        } else null

        val trackHalfW = 0.93f * ws
        val wheelPositions = listOf(
            Vec3(-trackHalfW, 0.35f, 1.32f * ls),   // Front Left
            Vec3(trackHalfW, 0.35f, 1.32f * ls),    // Front Right
            Vec3(-trackHalfW, 0.36f, -1.32f * ls),  // Rear Left
            Vec3(trackHalfW, 0.36f, -1.32f * ls)    // Rear Right
        )

        return BuiltCarMeshes(
            bodyMesh = bodyBuilder.buildGpuBuffer(),
            trimMesh = trimBuilder.buildGpuBuffer(),
            spoilerMesh = spoilerBuilder.buildGpuBuffer(),
            wheelMesh = wheelMesh,
            underglowMesh = underglowMesh,
            wheelPositions = wheelPositions
        )
    }

    private fun buildWheelMesh(rimStyle: RimStyle, rimRgb: FloatArray): MeshGpuBuffer {
        val wb = MeshDataBuilder(2500)
        val tireRgb = floatArrayOf(0.08f, 0.08f, 0.09f)
        val discRgb = floatArrayOf(0.55f, 0.58f, 0.62f)
        val caliperRgb = floatArrayOf(1.0f, 0.18f, 0.22f)

        // Outer rubber tire
        wb.addCylinderX(0f, 0f, 0f, radius = 0.35f, width = 0.28f, segments = 22, rgb = tireRgb)
        // Inner brake disc
        wb.addCylinderX(0f, 0f, 0f, radius = 0.21f, width = 0.06f, segments = 16, rgb = discRgb)
        // Brake caliper accent
        wb.addBox(0f, 0.14f, 0.11f, 0.055f, 0.07f, 0.06f, caliperRgb)

        // Rim outer barrel lip (left & right faces)
        val rimRadius = 0.245f
        val rimOffset = 0.145f - rimStyle.dishDepth * 0.25f
        wb.addCylinderX(0f, 0f, 0f, radius = rimRadius, width = 0.295f, segments = 20, rgb = rimRgb)

        // Center hub
        wb.addCylinderX(0f, 0f, 0f, radius = 0.075f, width = 0.31f, segments = 12, rgb = floatArrayOf(0.15f, 0.15f, 0.18f))

        // Spokes on both wheel faces so left & right wheels both show crisp 3D spokes
        val spokes = rimStyle.spokeCount
        val spokeThickness = when (rimStyle) {
            RimStyle.SPORT_5 -> 0.042f
            RimStyle.TURBINE_10 -> 0.022f
            RimStyle.DEEP_DISH -> 0.036f
            RimStyle.CARBON_AERO -> 0.055f
        }
        val spokeRgb = if (rimStyle == RimStyle.CARBON_AERO) floatArrayOf(0.14f, 0.15f, 0.18f) else rimRgb

        for (side in listOf(-rimOffset, rimOffset)) {
            for (i in 0 until spokes) {
                val angle = (i.toFloat() / spokes) * (2f * PI.toFloat())
                val tilt = if (rimStyle == RimStyle.TURBINE_10) 0.25f else 0f
                val midR = 0.145f
                val cy = cos(angle + tilt) * midR
                val cz = sin(angle + tilt) * midR
                wb.addBox(side, cy, cz, 0.025f, spokeThickness, spokeThickness, spokeRgb)
                val outerR = 0.205f
                wb.addBox(side, cos(angle) * outerR, sin(angle) * outerR, 0.025f, spokeThickness * 0.85f, spokeThickness * 0.85f, rimRgb)
            }
        }
        return wb.buildGpuBuffer()
    }

    private fun getSectionsForArchetype(archetype: CarBodyArchetype): List<CrossSection> = when (archetype) {
        CarBodyArchetype.SPORT_COUPE -> listOf(
            CrossSection(2.15f, 0.88f, 0.16f, 0.42f, 0.46f, 0.72f),
            CrossSection(1.55f, 0.96f, 0.15f, 0.56f, 0.64f, 0.80f),
            CrossSection(0.68f, 0.98f, 0.15f, 0.68f, 0.72f, 0.84f),
            CrossSection(0.05f, 0.96f, 0.15f, 0.72f, 1.18f, 0.68f),
            CrossSection(-0.90f, 0.97f, 0.15f, 0.74f, 1.14f, 0.66f),
            CrossSection(-1.68f, 0.98f, 0.16f, 0.78f, 0.84f, 0.78f),
            CrossSection(-2.15f, 0.92f, 0.18f, 0.74f, 0.78f, 0.76f)
        )
        CarBodyArchetype.MID_SUPERCAR -> listOf(
            CrossSection(2.20f, 0.92f, 0.13f, 0.36f, 0.40f, 0.74f),
            CrossSection(1.45f, 1.00f, 0.12f, 0.52f, 0.58f, 0.82f),
            CrossSection(0.65f, 1.02f, 0.12f, 0.62f, 0.68f, 0.80f),
            CrossSection(0.05f, 1.02f, 0.12f, 0.66f, 1.08f, 0.64f),
            CrossSection(-0.85f, 1.04f, 0.12f, 0.70f, 1.04f, 0.62f),
            CrossSection(-1.70f, 1.03f, 0.14f, 0.74f, 0.78f, 0.82f),
            CrossSection(-2.18f, 0.96f, 0.16f, 0.68f, 0.72f, 0.80f)
        )
        CarBodyArchetype.MUSCLE_GT -> listOf(
            CrossSection(2.22f, 0.95f, 0.18f, 0.52f, 0.56f, 0.80f),
            CrossSection(1.50f, 1.00f, 0.17f, 0.66f, 0.72f, 0.86f),
            CrossSection(0.55f, 1.01f, 0.17f, 0.74f, 0.78f, 0.86f),
            CrossSection(-0.05f, 1.00f, 0.17f, 0.78f, 1.24f, 0.72f),
            CrossSection(-0.95f, 1.02f, 0.17f, 0.80f, 1.20f, 0.70f),
            CrossSection(-1.72f, 1.02f, 0.18f, 0.84f, 0.88f, 0.84f),
            CrossSection(-2.22f, 0.96f, 0.20f, 0.80f, 0.82f, 0.82f)
        )
        CarBodyArchetype.JDM_DRIFT -> listOf(
            CrossSection(2.14f, 0.92f, 0.14f, 0.44f, 0.48f, 0.76f),
            CrossSection(1.48f, 0.98f, 0.13f, 0.58f, 0.64f, 0.82f),
            CrossSection(0.62f, 0.99f, 0.13f, 0.68f, 0.72f, 0.84f),
            CrossSection(-0.02f, 0.98f, 0.13f, 0.72f, 1.17f, 0.68f),
            CrossSection(-0.92f, 1.00f, 0.13f, 0.74f, 1.13f, 0.66f),
            CrossSection(-1.68f, 1.00f, 0.14f, 0.78f, 0.82f, 0.80f),
            CrossSection(-2.14f, 0.94f, 0.16f, 0.74f, 0.76f, 0.78f)
        )
        CarBodyArchetype.PROTOTYPE_LM -> listOf(
            CrossSection(2.26f, 0.96f, 0.11f, 0.34f, 0.38f, 0.56f),
            CrossSection(1.45f, 1.04f, 0.10f, 0.50f, 0.54f, 0.60f),
            CrossSection(0.65f, 1.04f, 0.10f, 0.58f, 0.64f, 0.62f),
            CrossSection(0.05f, 1.05f, 0.10f, 0.60f, 1.02f, 0.54f),
            CrossSection(-0.85f, 1.06f, 0.10f, 0.62f, 0.96f, 0.52f),
            CrossSection(-1.75f, 1.05f, 0.12f, 0.64f, 0.70f, 0.80f),
            CrossSection(-2.24f, 1.00f, 0.14f, 0.60f, 0.64f, 0.78f)
        )
        CarBodyArchetype.ROADSTER_V12 -> listOf(
            CrossSection(2.16f, 0.92f, 0.14f, 0.40f, 0.44f, 0.76f),
            CrossSection(1.48f, 0.99f, 0.13f, 0.55f, 0.60f, 0.82f),
            CrossSection(0.65f, 1.00f, 0.13f, 0.66f, 0.70f, 0.82f),
            CrossSection(0.00f, 0.99f, 0.13f, 0.68f, 0.96f, 0.66f),
            CrossSection(-0.85f, 1.00f, 0.13f, 0.72f, 0.82f, 0.74f),
            CrossSection(-1.68f, 1.00f, 0.14f, 0.76f, 0.80f, 0.80f),
            CrossSection(-2.14f, 0.94f, 0.16f, 0.70f, 0.74f, 0.78f)
        )
    }
}
