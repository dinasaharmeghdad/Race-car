package com.example.engine3d

import com.example.model.TrackSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackSample(
    val pos: Vec3,
    val tangent: Vec3,
    val right: Vec3,
    val up: Vec3,
    val distFromStart: Float,
    val t: Float
)

class BuiltTrack(
    val spec: TrackSpec,
    val samples: List<TrackSample>,
    val totalLength: Float,
    val roadHalfWidth: Float,
    val worldMesh: MeshGpuBuffer,
    val accentMesh: MeshGpuBuffer,
    val minimapPoints: List<Pair<Float, Float>>
) {
    fun sampleAtProgress(progressNormalized: Float): TrackSample {
        val wrapped = ((progressNormalized % 1f) + 1f) % 1f
        val idx = (wrapped * samples.size).toInt().coerceIn(0, samples.lastIndex)
        return samples[idx]
    }

    fun findClosestSampleIndex(x: Float, z: Float, hintIndex: Int): Int {
        var bestIdx = hintIndex
        var bestDistSq = Float.MAX_VALUE
        val n = samples.size
        // Search local window around hint first for speed, fallback to full scan if far
        val window = 28
        for (offset in -window..window) {
            val idx = ((hintIndex + offset) % n + n) % n
            val p = samples[idx].pos
            val dx = x - p.x
            val dz = z - p.z
            val d2 = dx * dx + dz * dz
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestIdx = idx
            }
        }
        if (bestDistSq > 1600f) {
            for (i in samples.indices) {
                val p = samples[i].pos
                val dx = x - p.x
                val dz = z - p.z
                val d2 = dx * dx + dz * dz
                if (d2 < bestDistSq) {
                    bestDistSq = d2
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }

    fun release() {
        worldMesh.release()
        accentMesh.release()
    }
}

object TrackBuilder {

    fun buildTrack(spec: TrackSpec): BuiltTrack {
        val controlPoints = controlPointsFor(spec.id)
        val segmentCount = 260
        val roadHalfWidth = 9.5f

        val rawPositions = ArrayList<Vec3>(segmentCount)
        for (i in 0 until segmentCount) {
            rawPositions.add(catmullRomClosed(controlPoints, i.toFloat() / segmentCount))
        }

        val samples = ArrayList<TrackSample>(segmentCount)
        var cumulativeDist = 0f
        for (i in 0 until segmentCount) {
            val prev = rawPositions[(i - 1 + segmentCount) % segmentCount]
            val curr = rawPositions[i]
            val next = rawPositions[(i + 1) % segmentCount]
            if (i > 0) {
                cumulativeDist += (curr - prev).length()
            }
            val tangent = (next - prev).normalized()
            val worldUp = Vec3(0f, 1f, 0f)
            val right = tangent.cross(worldUp).normalized()
            val up = right.cross(tangent).normalized()
            samples.add(
                TrackSample(
                    pos = curr,
                    tangent = tangent,
                    right = right,
                    up = up,
                    distFromStart = cumulativeDist,
                    t = i.toFloat() / segmentCount
                )
            )
        }
        val totalLength = cumulativeDist + (rawPositions[0] - rawPositions.last()).length()

        val worldBuilder = MeshDataBuilder(32000)
        val accentBuilder = MeshDataBuilder(12000)

        // Vast ground plane beneath circuit
        val groundSize = 1800f
        worldBuilder.addQuad(
            Vec3(-groundSize, -0.35f, -groundSize),
            Vec3(-groundSize, -0.35f, groundSize),
            Vec3(groundSize, -0.35f, groundSize),
            Vec3(groundSize, -0.35f, -groundSize),
            spec.groundRgb
        )

        val shoulderRgb = floatArrayOf(0.14f, 0.14f, 0.16f)
        val railRgb = floatArrayOf(0.42f, 0.45f, 0.50f)
        val whiteRgb = floatArrayOf(0.92f, 0.94f, 0.96f)
        val blackRgb = floatArrayOf(0.08f, 0.08f, 0.10f)

        // Road surface, curbs, center lane markings, guardrails, and scenery
        for (i in 0 until segmentCount) {
            val s0 = samples[i]
            val s1 = samples[(i + 1) % segmentCount]

            val r0 = s0.right
            val r1 = s1.right

            // Asphalt road quad (slightly raised above ground)
            val roadL0 = s0.pos - r0 * roadHalfWidth
            val roadR0 = s0.pos + r0 * roadHalfWidth
            val roadL1 = s1.pos - r1 * roadHalfWidth
            val roadR1 = s1.pos + r1 * roadHalfWidth

            // Subtle longitudinal asphalt variation for speed perception
            val shade = if (i % 2 == 0) 1.0f else 0.94f
            val asphaltRgb = floatArrayOf(
                spec.roadRgb[0] * shade,
                spec.roadRgb[1] * shade,
                spec.roadRgb[2] * shade
            )
            worldBuilder.addQuad(roadL0, roadR0, roadR1, roadL1, asphaltRgb)

            // Raised Apex Curbs on both edges
            val curbWidth = 1.25f
            val curbRgb = if ((i / 2) % 2 == 0) spec.curbPrimaryRgb else spec.curbSecondaryRgb
            val curbEmissive = if (spec.isNight) 0.55f else 0.0f

            // Left curb
            val cLL0 = s0.pos - r0 * (roadHalfWidth + curbWidth) + Vec3(0f, 0.04f, 0f)
            val cLR0 = s0.pos - r0 * roadHalfWidth + Vec3(0f, 0.06f, 0f)
            val cLL1 = s1.pos - r1 * (roadHalfWidth + curbWidth) + Vec3(0f, 0.04f, 0f)
            val cLR1 = s1.pos - r1 * roadHalfWidth + Vec3(0f, 0.06f, 0f)
            worldBuilder.addQuad(cLL0, cLR0, cLR1, cLL1, curbRgb, emissive = curbEmissive)

            // Right curb
            val cRL0 = s0.pos + r0 * roadHalfWidth + Vec3(0f, 0.06f, 0f)
            val cRR0 = s0.pos + r0 * (roadHalfWidth + curbWidth) + Vec3(0f, 0.04f, 0f)
            val cRL1 = s1.pos + r1 * roadHalfWidth + Vec3(0f, 0.06f, 0f)
            val cRR1 = s1.pos + r1 * (roadHalfWidth + curbWidth) + Vec3(0f, 0.04f, 0f)
            worldBuilder.addQuad(cRL0, cRR0, cRR1, cRL1, curbRgb, emissive = curbEmissive)

            // Outer & Inner Guardrails
            val wallDist = roadHalfWidth + curbWidth + 0.45f
            val wallHeight = 1.05f
            for (sideSign in listOf(-1f, 1f)) {
                val b0 = s0.pos + r0 * (wallDist * sideSign)
                val t0 = b0 + Vec3(0f, wallHeight, 0f)
                val b1 = s1.pos + r1 * (wallDist * sideSign)
                val t1 = b1 + Vec3(0f, wallHeight, 0f)
                if (sideSign > 0f) {
                    worldBuilder.addQuad(b0, b1, t1, t0, railRgb)
                } else {
                    worldBuilder.addQuad(b1, b0, t0, t1, railRgb)
                }

                // Glowing rail strip at top
                if (spec.isNight || i % 4 == 0) {
                    val stripRgb = if (i % 6 < 3) spec.accentRgb else shoulderRgb
                    val stEmissive = if (spec.isNight && i % 2 == 0) 0.85f else 0.25f
                    val sb0 = b0 + Vec3(0f, wallHeight - 0.20f, 0f)
                    val sb1 = b1 + Vec3(0f, wallHeight - 0.20f, 0f)
                    if (sideSign > 0f) {
                        accentBuilder.addQuad(sb0, sb1, t1, t0, stripRgb, emissive = stEmissive)
                    } else {
                        accentBuilder.addQuad(sb1, sb0, t0, t1, stripRgb, emissive = stEmissive)
                    }
                }
            }

            // Center dashed road stripe
            if (i % 3 == 0) {
                val stripeW = 0.22f
                val lift = Vec3(0f, 0.025f, 0f)
                accentBuilder.addQuad(
                    s0.pos - r0 * stripeW + lift,
                    s0.pos + r0 * stripeW + lift,
                    s1.pos + r1 * stripeW + lift,
                    s1.pos - r1 * stripeW + lift,
                    if (spec.isNight) spec.accentRgb else whiteRgb,
                    emissive = if (spec.isNight) 0.4f else 0f
                )
            }

            // Roadside Scenery every few segments
            if (i % 5 == 0) {
                addSceneryAtSegment(spec, s0, roadHalfWidth + 6.5f, i, worldBuilder, accentBuilder)
            }
        }

        // Checkered Start / Finish Line & Grand Overhead Gantry Arch at sample 0
        val start = samples[0]
        val finishLift = Vec3(0f, 0.03f, 0f)
        val numChecks = 10
        val checkSpan = (roadHalfWidth * 2f) / numChecks
        for (c in 0 until numChecks) {
            val x0 = -roadHalfWidth + c * checkSpan
            val x1 = x0 + checkSpan
            val col = if (c % 2 == 0) whiteRgb else blackRgb
            accentBuilder.addQuad(
                start.pos + start.right * x0 + finishLift,
                start.pos + start.right * x1 + finishLift,
                start.pos + start.tangent * 2.4f + start.right * x1 + finishLift,
                start.pos + start.tangent * 2.4f + start.right * x0 + finishLift,
                col,
                emissive = 0.2f
            )
        }

        // Gantry pillars and overhead banner
        val pillarOffset = roadHalfWidth + 2.2f
        val leftBase = start.pos - start.right * pillarOffset
        val rightBase = start.pos + start.right * pillarOffset
        worldBuilder.addBox(leftBase.x, leftBase.y + 4.0f, leftBase.z, 0.6f, 4.0f, 0.6f, railRgb)
        worldBuilder.addBox(rightBase.x, rightBase.y + 4.0f, rightBase.z, 0.6f, 4.0f, 0.6f, railRgb)
        val midTop = (leftBase + rightBase) * 0.5f + Vec3(0f, 7.6f, 0f)
        val archSpan = (rightBase - leftBase).length() * 0.52f
        // Rotate overhead banner along start.right
        val archThick = start.tangent * 0.7f
        val archH = Vec3(0f, 1.4f, 0f)
        val archL = start.pos - start.right * pillarOffset + Vec3(0f, 6.8f, 0f)
        val archR = start.pos + start.right * pillarOffset + Vec3(0f, 6.8f, 0f)
        accentBuilder.addQuad(archL - archThick, archR - archThick, archR - archThick + archH, archL - archThick + archH, spec.accentRgb, emissive = 0.9f)
        accentBuilder.addQuad(archR + archThick, archL + archThick, archL + archThick + archH, archR + archThick + archH, spec.accentRgb, emissive = 0.9f)
        // Gantry start lights
        for (lIdx in -1..1) {
            val lp = midTop + start.right * (lIdx * 2.2f) - Vec3(0f, 0.6f, 0f)
            accentBuilder.addBox(lp.x, lp.y, lp.z, 0.45f, 0.45f, 0.9f, floatArrayOf(1f, 0.15f, 0.22f), emissive = 1.0f)
        }
        // Keep unused variable warning clear
        if (archSpan < 0f) Unit

        // Normalized minimap points (0..1)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (s in samples) {
            if (s.pos.x < minX) minX = s.pos.x
            if (s.pos.x > maxX) maxX = s.pos.x
            if (s.pos.z < minZ) minZ = s.pos.z
            if (s.pos.z > maxZ) maxZ = s.pos.z
        }
        val span = maxOf(maxX - minX, maxZ - minZ).coerceAtLeast(1f)
        val minimapPoints = samples.filterIndexed { idx, _ -> idx % 2 == 0 }.map { s ->
            val nx = (s.pos.x - minX) / span
            val nz = (s.pos.z - minZ) / span
            nx to nz
        }

        return BuiltTrack(
            spec = spec,
            samples = samples,
            totalLength = totalLength,
            roadHalfWidth = roadHalfWidth,
            worldMesh = worldBuilder.buildGpuBuffer(),
            accentMesh = accentBuilder.buildGpuBuffer(),
            minimapPoints = minimapPoints
        )
    }

    private fun addSceneryAtSegment(
        spec: TrackSpec,
        s: TrackSample,
        baseOffset: Float,
        idx: Int,
        worldBuilder: MeshDataBuilder,
        accentBuilder: MeshDataBuilder
    ) {
        val side = if (idx % 10 == 0) 1f else -1f
        val dist = baseOffset + ((idx * 17) % 24).toFloat()
        val p = s.pos + s.right * (side * dist)
        val groundY = -0.35f

        when (spec.id) {
            "neon_tokyo" -> {
                // Tall neon-lit cyberpunk skyscrapers + street light arches
                val h = 22f + ((idx * 13) % 42).toFloat()
                val w = 4.5f + ((idx * 7) % 5).toFloat()
                val bldgRgb = floatArrayOf(0.06f, 0.08f, 0.14f)
                worldBuilder.addBox(p.x, groundY + h * 0.5f, p.z, w, h * 0.5f, w, bldgRgb)
                val windowRgb = if (idx % 2 == 0) floatArrayOf(0.0f, 0.88f, 1.0f) else floatArrayOf(1.0f, 0.16f, 0.55f)
                accentBuilder.addBox(p.x, groundY + h * 0.72f, p.z, w + 0.12f, 0.45f, w + 0.12f, windowRgb, emissive = 1.0f)
                accentBuilder.addBox(p.x, groundY + h * 0.42f, p.z, w + 0.12f, 0.35f, w + 0.12f, windowRgb, emissive = 0.9f)
                // Street lamp post near curb
                val lampPos = s.pos + s.right * (side * (12.5f))
                worldBuilder.addBox(lampPos.x, lampPos.y + 3.2f, lampPos.z, 0.16f, 3.2f, 0.16f, floatArrayOf(0.2f, 0.22f, 0.28f))
                accentBuilder.addBox(lampPos.x, lampPos.y + 6.3f, lampPos.z, 0.6f, 0.14f, 0.6f, windowRgb, emissive = 1.0f)
            }
            "alpine_pass" -> {
                // Pine trees near track + distant snow-capped mountain peaks
                val treeScale = 1.0f + ((idx * 3) % 5) * 0.25f
                val trunkRgb = floatArrayOf(0.28f, 0.18f, 0.12f)
                val pineRgb = floatArrayOf(0.08f, 0.28f, 0.14f)
                worldBuilder.addConeOrCylinderY(p.x, groundY, p.z, 0.4f, 0.25f, 2.2f * treeScale, 6, trunkRgb)
                worldBuilder.addConeOrCylinderY(p.x, groundY + 1.8f * treeScale, p.z, 2.5f * treeScale, 0.0f, 6.2f * treeScale, 7, pineRgb)
                if (idx % 15 == 0) {
                    val mPos = s.pos + s.right * (side * (55f + (idx % 20)))
                    val mh = 34f + (idx % 25)
                    worldBuilder.addConeOrCylinderY(mPos.x, groundY, mPos.z, 22f, 2.5f, mh, 6, floatArrayOf(0.28f, 0.30f, 0.36f))
                    worldBuilder.addConeOrCylinderY(mPos.x, groundY + mh * 0.68f, mPos.z, 8.5f, 0.0f, mh * 0.35f, 6, floatArrayOf(0.92f, 0.95f, 0.98f))
                }
            }
            else -> {
                // Desert Canyon red sandstone mesas and spires
                val rockH = 12f + ((idx * 11) % 30).toFloat()
                val rockW = 5.0f + ((idx * 5) % 8).toFloat()
                val mesaRgb = if (idx % 2 == 0) floatArrayOf(0.68f, 0.30f, 0.16f) else floatArrayOf(0.56f, 0.24f, 0.13f)
                worldBuilder.addConeOrCylinderY(p.x, groundY, p.z, rockW, rockW * 0.68f, rockH, 6, mesaRgb)
            }
        }
    }

    private fun controlPointsFor(trackId: String): List<Vec3> = when (trackId) {
        "neon_tokyo" -> listOf(
            Vec3(0f, 0f, 0f),
            Vec3(0f, 0f, 130f),
            Vec3(75f, 0f, 220f),
            Vec3(195f, 0f, 230f),
            Vec3(260f, 0f, 140f),
            Vec3(210f, 0f, 55f),
            Vec3(285f, 0f, -45f),
            Vec3(250f, 0f, -165f),
            Vec3(120f, 0f, -205f),
            Vec3(25f, 0f, -130f)
        )
        "alpine_pass" -> listOf(
            Vec3(0f, 0f, 0f),
            Vec3(0f, 2.5f, 140f),
            Vec3(90f, 7.0f, 240f),
            Vec3(220f, 11.0f, 210f),
            Vec3(260f, 8.0f, 90f),
            Vec3(165f, 4.0f, 35f),
            Vec3(235f, 1.5f, -75f),
            Vec3(195f, -1.0f, -195f),
            Vec3(70f, -1.5f, -190f),
            Vec3(-15f, 0f, -105f)
        )
        else -> listOf(
            Vec3(0f, 0f, 0f),
            Vec3(0f, 0f, 160f),
            Vec3(110f, 3.0f, 270f),
            Vec3(260f, 4.5f, 250f),
            Vec3(320f, 1.5f, 115f),
            Vec3(245f, 0f, 15f),
            Vec3(310f, 2.0f, -105f),
            Vec3(225f, 0f, -230f),
            Vec3(80f, -1.0f, -210f),
            Vec3(10f, 0f, -110f)
        )
    }

    fun buildGarageFloor(): Pair<MeshGpuBuffer, MeshGpuBuffer> {
        val floorBuilder = MeshDataBuilder(1200)
        val ringBuilder = MeshDataBuilder(2400)
        val floorRgb = floatArrayOf(0.05f, 0.06f, 0.09f)
        val platterRgb = floatArrayOf(0.09f, 0.11f, 0.16f)
        val ringCyan = floatArrayOf(0.0f, 0.85f, 1.0f)
        val ringCrimson = floatArrayOf(1.0f, 0.18f, 0.30f)

        floorBuilder.addQuad(
            Vec3(-40f, -0.02f, -40f),
            Vec3(-40f, -0.02f, 40f),
            Vec3(40f, -0.02f, 40f),
            Vec3(40f, -0.02f, -40f),
            floorRgb
        )
        // Raised circular turntable platform
        val segs = 48
        val radius = 3.25f
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segs) * (2f * PI.toFloat())
            val p0 = Vec3(cos(a0) * radius, 0.0f, sin(a0) * radius)
            val p1 = Vec3(cos(a1) * radius, 0.0f, sin(a1) * radius)
            floorBuilder.addTriangle(Vec3(0f, 0.0f, 0f), p1, p0, platterRgb)

            // Outer glowing ring
            val rIn = 3.18f; val rOut = 3.28f
            val i0 = Vec3(cos(a0) * rIn, 0.01f, sin(a0) * rIn)
            val i1 = Vec3(cos(a1) * rIn, 0.01f, sin(a1) * rIn)
            val o0 = Vec3(cos(a0) * rOut, 0.01f, sin(a0) * rOut)
            val o1 = Vec3(cos(a1) * rOut, 0.01f, sin(a1) * rOut)
            ringBuilder.addQuad(i0, o0, o1, i1, if (i % 4 < 2) ringCyan else ringCrimson, emissive = 1.0f)
        }
        return floorBuilder.buildGpuBuffer() to ringBuilder.buildGpuBuffer()
    }
}
