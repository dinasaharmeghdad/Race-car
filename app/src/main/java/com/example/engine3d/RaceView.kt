package com.example.engine3d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CarSetup
import com.example.model.CarSpec
import com.example.model.TrackSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class RaceRenderer(
    private val playerSpec: CarSpec,
    private val playerSetup: CarSetup,
    private val trackSpec: TrackSpec,
    private val onTelemetry: (RaceTelemetry) -> Unit
) : GLSurfaceView.Renderer {

    private var pbrShader: RacingShaderProgram? = null
    private var skyProgram: Int = 0
    private var skyBuffer: FloatBuffer? = null
    private var builtTrack: BuiltTrack? = null
    private var simulation: RaceSimulation? = null
    private val racerMeshes = mutableListOf<BuiltCarMeshes>()
    private var flameMesh: MeshGpuBuffer? = null

    @Volatile
    var inputState: RaceInputState = RaceInputState()

    @Volatile
    var isPaused: Boolean = false
        set(value) {
            field = value
            simulation?.isPaused = value
        }

    private var lastFrameNs: Long = 0L
    private var lastTelemetryMs: Long = 0L
    private var viewportAspect: Float = 16f / 9f

    private var camPos = Vec3(0f, 3f, -10f)
    private var camLook = Vec3(0f, 1f, 0f)
    private var currentFov = 58f

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun cycleCameraMode() {
        val sim = simulation ?: return
        val modes = CameraMode.entries
        sim.cameraMode = modes[(sim.cameraMode.ordinal + 1) % modes.size]
    }

    fun restartRace() {
        val track = builtTrack ?: return
        simulation = RaceSimulation(playerSpec, playerSetup, trackSpec, track)
        isPaused = false
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(trackSpec.skyBottomRgb[0], trackSpec.skyBottomRgb[1], trackSpec.skyBottomRgb[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        pbrShader = RacingShaderProgram(
            RacingShaderProgram.PBR_VERTEX_SHADER,
            RacingShaderProgram.PBR_FRAGMENT_SHADER
        )
        setupSkyGradient()

        val track = TrackBuilder.buildTrack(trackSpec)
        builtTrack = track
        val sim = RaceSimulation(playerSpec, playerSetup, trackSpec, track)
        simulation = sim

        racerMeshes.forEach { it.release() }
        racerMeshes.clear()
        sim.racers.forEach { racer ->
            racerMeshes.add(CarMeshBuilder.buildCarMeshes(racer.spec, racer.setup))
        }

        // Nitrous oxide twin exhaust flame mesh
        val fb = MeshDataBuilder(256)
        val flameCyan = floatArrayOf(0.15f, 0.85f, 1.0f)
        fb.addConeOrCylinderY(-0.41f, 0f, -2.35f, 0.11f, 0.0f, 1.0f, 8, flameCyan, emissive = 1.0f)
        fb.addConeOrCylinderY(0.41f, 0f, -2.35f, 0.11f, 0.0f, 1.0f, 8, flameCyan, emissive = 1.0f)
        flameMesh = fb.buildGpuBuffer()

        val p = sim.player
        val fwd = Vec3(sin(p.yawRad), 0f, cos(p.yawRad))
        camPos = Vec3(p.x, p.y, p.z) - fwd * 7.5f + Vec3(0f, 2.7f, 0f)
        camLook = Vec3(p.x, p.y + 0.9f, p.z) + fwd * 8f

        lastFrameNs = System.nanoTime()
        lastTelemetryMs = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportAspect = width.toFloat() / maxOf(height, 1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, currentFov, viewportAspect, 0.25f, 650f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        val sim = simulation ?: return
        val track = builtTrack ?: return

        sim.step(dt, inputState)

        // Update camera smoothly
        val p = sim.player
        val fwd = Vec3(sin(p.yawRad), 0f, cos(p.yawRad))
        val targetFov = when {
            p.isNitroActive -> 71f
            else -> 56f + (p.speed / 75f).coerceIn(0f, 1f) * 9f
        }
        currentFov = lerpFloat(currentFov, targetFov, (dt * 6f).coerceAtMost(1f))
        Matrix.perspectiveM(projMatrix, 0, currentFov, viewportAspect, 0.25f, 650f)

        val (desiredCamPos, desiredLook) = when (sim.cameraMode) {
            CameraMode.CHASE -> {
                val cPos = Vec3(p.x, p.y, p.z) - fwd * 7.2f + Vec3(0f, 2.55f, 0f)
                val lPos = Vec3(p.x, p.y + 0.85f, p.z) + fwd * 12f
                cPos to lPos
            }
            CameraMode.HOOD -> {
                val cPos = Vec3(p.x, p.y + 1.18f, p.z) + fwd * 0.65f
                val lPos = cPos + fwd * 18f - Vec3(0f, 0.25f, 0f)
                cPos to lPos
            }
            CameraMode.FAR -> {
                val cPos = Vec3(p.x, p.y, p.z) - fwd * 10.5f + Vec3(0f, 4.1f, 0f)
                val lPos = Vec3(p.x, p.y + 0.8f, p.z) + fwd * 10f
                cPos to lPos
            }
        }

        val camLerp = if (sim.cameraMode == CameraMode.HOOD) 1.0f else (dt * 11f).coerceAtMost(1f)
        camPos = lerpVec3(camPos, desiredCamPos, camLerp)
        camLook = lerpVec3(camLook, desiredLook, (dt * 14f).coerceAtMost(1f))

        Matrix.setLookAtM(viewMatrix, 0, camPos.x, camPos.y, camPos.z, camLook.x, camLook.y, camLook.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 1. Draw atmospheric gradient sky backdrop
        drawSky()

        // 2. Draw 3D World & Track
        val prog = pbrShader ?: return
        prog.use()
        GLES20.glUniform3f(prog.uCameraPos, camPos.x, camPos.y, camPos.z)
        if (trackSpec.isNight) {
            GLES20.glUniform3f(prog.uLightDir, -0.35f, 0.80f, 0.45f)
            GLES20.glUniform3f(prog.uSkyColor, 0.28f, 0.36f, 0.65f)
            GLES20.glUniform3f(prog.uGroundColor, 0.08f, 0.08f, 0.14f)
            GLES20.glUniform3f(prog.uFogColor, trackSpec.skyBottomRgb[0], trackSpec.skyBottomRgb[1], trackSpec.skyBottomRgb[2])
            GLES20.glUniform1f(prog.uFogDensity, 0.0024f)
        } else {
            GLES20.glUniform3f(prog.uLightDir, 0.55f, 0.82f, 0.35f)
            GLES20.glUniform3f(prog.uSkyColor, 0.65f, 0.72f, 0.88f)
            GLES20.glUniform3f(prog.uGroundColor, 0.32f, 0.25f, 0.20f)
            GLES20.glUniform3f(prog.uFogColor, trackSpec.skyBottomRgb[0], trackSpec.skyBottomRgb[1], trackSpec.skyBottomRgb[2])
            GLES20.glUniform1f(prog.uFogDensity, 0.0018f)
        }

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(prog.uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(prog.uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(prog.uRoughness, if (trackSpec.isNight) 0.22f else 0.55f)
        GLES20.glUniform1f(prog.uMetallic, if (trackSpec.isNight) 0.35f else 0.08f)
        GLES20.glUniform1f(prog.uPearl, 0.0f)
        GLES20.glUniform1f(prog.uAlpha, 1.0f)
        track.worldMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
        track.accentMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

        // 3. Draw All 4 3D Cars
        sim.racers.forEachIndexed { idx, racer ->
            if (idx >= racerMeshes.size) return@forEachIndexed
            // Skip player body in HOOD cam so camera doesn't clip interior
            if (racer.isPlayer && sim.cameraMode == CameraMode.HOOD) return@forEachIndexed

            val meshes = racerMeshes[idx]
            val finish = racer.setup.finishEnum

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, racer.x, racer.y, racer.z)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(racer.yawRad.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(racer.pitchRad.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(racer.rollRad.toDouble()).toFloat(), 0f, 0f, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            GLES20.glUniformMatrix4fv(prog.uModelMatrix, 1, false, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(prog.uMvpMatrix, 1, false, mvpMatrix, 0)

            meshes.underglowMesh?.let { glow ->
                GLES20.glUniform1f(prog.uAlpha, 0.45f)
                glow.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
                GLES20.glUniform1f(prog.uAlpha, 1.0f)
            }

            GLES20.glUniform1f(prog.uRoughness, finish.roughness)
            GLES20.glUniform1f(prog.uMetallic, finish.metallic)
            GLES20.glUniform1f(prog.uPearl, finish.pearl)
            meshes.bodyMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

            GLES20.glUniform1f(prog.uRoughness, 0.14f)
            GLES20.glUniform1f(prog.uMetallic, 0.45f)
            GLES20.glUniform1f(prog.uPearl, 0.0f)
            meshes.trimMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

            GLES20.glUniform1f(prog.uRoughness, finish.roughness)
            GLES20.glUniform1f(prog.uMetallic, finish.metallic)
            meshes.spoilerMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

            // Nitrous exhaust flame cones
            if (racer.isNitroActive) {
                val flameMatrix = FloatArray(16)
                Matrix.setIdentityM(flameMatrix, 0)
                Matrix.translateM(flameMatrix, 0, racer.x, racer.y + 0.30f, racer.z)
                Matrix.rotateM(flameMatrix, 0, Math.toDegrees(racer.yawRad.toDouble()).toFloat(), 0f, 1f, 0f)
                // Rotate cone along -Z
                Matrix.translateM(flameMatrix, 0, 0f, 0f, -2.25f)
                Matrix.rotateM(flameMatrix, 0, -90f, 1f, 0f, 0f)
                val pulse = 0.85f + 0.30f * sin((nowNs / 40_000_000.0)).toFloat()
                Matrix.scaleM(flameMatrix, 0, 1f, pulse, 1f)
                Matrix.translateM(flameMatrix, 0, 0f, 0f, 2.35f)
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, flameMatrix, 0)
                GLES20.glUniformMatrix4fv(prog.uModelMatrix, 1, false, flameMatrix, 0)
                GLES20.glUniformMatrix4fv(prog.uMvpMatrix, 1, false, mvpMatrix, 0)
                GLES20.glUniform1f(prog.uAlpha, 0.85f)
                flameMesh?.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
                GLES20.glUniform1f(prog.uAlpha, 1.0f)
            }

            // 4 spinning/steering wheels
            val spinDeg = Math.toDegrees(racer.wheelSpinRad.toDouble()).toFloat()
            val steerDeg = Math.toDegrees(racer.steerVisual.toDouble()).toFloat()
            meshes.wheelPositions.forEachIndexed { wIdx, wPos ->
                val wm = FloatArray(16)
                Matrix.setIdentityM(wm, 0)
                Matrix.translateM(wm, 0, racer.x, racer.y, racer.z)
                Matrix.rotateM(wm, 0, Math.toDegrees(racer.yawRad.toDouble()).toFloat(), 0f, 1f, 0f)
                Matrix.translateM(wm, 0, wPos.x, wPos.y, wPos.z)
                if (wIdx < 2) {
                    Matrix.rotateM(wm, 0, steerDeg, 0f, 1f, 0f)
                }
                Matrix.rotateM(wm, 0, spinDeg, 1f, 0f, 0f)
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, wm, 0)
                GLES20.glUniformMatrix4fv(prog.uModelMatrix, 1, false, wm, 0)
                GLES20.glUniformMatrix4fv(prog.uMvpMatrix, 1, false, mvpMatrix, 0)
                meshes.wheelMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
            }
        }

        // Push HUD telemetry ~20 times/sec
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastTelemetryMs >= 50L) {
            lastTelemetryMs = nowMs
            onTelemetry(sim.buildTelemetry())
        }
    }

    private fun setupSkyGradient() {
        val vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, RacingShaderProgram.SKY_VERTEX_SHADER)
            GLES20.glCompileShader(it)
        }
        val fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, RacingShaderProgram.SKY_FRAGMENT_SHADER)
            GLES20.glCompileShader(it)
        }
        skyProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        val top = trackSpec.skyTopRgb
        val bot = trackSpec.skyBottomRgb
        val verts = floatArrayOf(
            -1f, -1f, 0.999f, bot[0], bot[1], bot[2],
             1f, -1f, 0.999f, bot[0], bot[1], bot[2],
             1f,  1f, 0.999f, top[0], top[1], top[2],
            -1f, -1f, 0.999f, bot[0], bot[1], bot[2],
             1f,  1f, 0.999f, top[0], top[1], top[2],
            -1f,  1f, 0.999f, top[0], top[1], top[2]
        )
        skyBuffer = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts).apply { position(0) }
    }

    private fun drawSky() {
        val fb = skyBuffer ?: return
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(skyProgram)
        val posLoc = GLES20.glGetAttribLocation(skyProgram, "aPosition")
        val colLoc = GLES20.glGetAttribLocation(skyProgram, "aColor")

        fb.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 24, fb)

        fb.position(3)
        GLES20.glEnableVertexAttribArray(colLoc)
        GLES20.glVertexAttribPointer(colLoc, 3, GLES20.GL_FLOAT, false, 24, fb)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(colLoc)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
    }
}

class RaceGLSurfaceView(
    context: Context,
    carSpec: CarSpec,
    carSetup: CarSetup,
    trackSpec: TrackSpec,
    onTelemetry: (RaceTelemetry) -> Unit
) : GLSurfaceView(context) {
    val renderer = RaceRenderer(carSpec, carSetup, trackSpec, onTelemetry)

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

@Composable
fun RaceSurfaceView(
    carSpec: CarSpec,
    carSetup: CarSetup,
    trackSpec: TrackSpec,
    inputState: RaceInputState,
    isPaused: Boolean,
    onTelemetry: (RaceTelemetry) -> Unit,
    onViewReady: (RaceGLSurfaceView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glView = remember(carSpec.id, trackSpec.id) {
        RaceGLSurfaceView(context, carSpec, carSetup, trackSpec, onTelemetry).also {
            onViewReady(it)
        }
    }

    glView.renderer.inputState = inputState
    glView.renderer.isPaused = isPaused

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier
    )
}
