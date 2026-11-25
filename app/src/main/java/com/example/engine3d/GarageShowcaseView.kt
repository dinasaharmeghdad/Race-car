package com.example.engine3d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CarSetup
import com.example.model.CarSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class GarageRenderer : GLSurfaceView.Renderer {

    private var shader: RacingShaderProgram? = null
    private var skyShader: Int = 0
    private var floorMesh: MeshGpuBuffer? = null
    private var ringMesh: MeshGpuBuffer? = null
    private var carMeshes: BuiltCarMeshes? = null

    @Volatile
    private var pendingCarSpec: CarSpec? = null
    @Volatile
    private var pendingCarSetup: CarSetup? = null
    @Volatile
    private var needsRebuild: Boolean = false

    private var activeSetup: CarSetup? = null

    @Volatile
    var orbitYawDeg: Float = 38f
    @Volatile
    var orbitPitchDeg: Float = 16f
    @Volatile
    var autoRotate: Boolean = true

    private var lastFrameNs: Long = 0L

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private var cameraPos = floatArrayOf(4f, 2f, 5f)

    fun setCar(spec: CarSpec, setup: CarSetup) {
        pendingCarSpec = spec
        pendingCarSetup = setup
        activeSetup = setup
        needsRebuild = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.047f, 0.071f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        shader = RacingShaderProgram(
            RacingShaderProgram.PBR_VERTEX_SHADER,
            RacingShaderProgram.PBR_FRAGMENT_SHADER
        )
        val (floor, ring) = TrackBuilder.buildGarageFloor()
        floorMesh = floor
        ringMesh = ring
        needsRebuild = true
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / maxOf(height, 1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 42f, aspect, 0.2f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNs = now

        if (needsRebuild) {
            val spec = pendingCarSpec
            val setup = pendingCarSetup
            if (spec != null && setup != null) {
                carMeshes?.release()
                carMeshes = CarMeshBuilder.buildCarMeshes(spec, setup)
            }
            needsRebuild = false
        }

        if (autoRotate) {
            orbitYawDeg = (orbitYawDeg + dt * 22f) % 360f
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val yawRad = Math.toRadians(orbitYawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(orbitPitchDeg.toDouble()).toFloat()
        val radius = 5.4f
        val cx = cos(pitchRad) * sin(yawRad) * radius
        val cy = sin(pitchRad) * radius + 0.55f
        val cz = cos(pitchRad) * cos(yawRad) * radius
        cameraPos = floatArrayOf(cx, cy, cz)

        Matrix.setLookAtM(viewMatrix, 0, cx, cy, cz, 0f, 0.58f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val prog = shader ?: return
        prog.use()
        GLES20.glUniform3fv(prog.uCameraPos, 1, cameraPos, 0)
        GLES20.glUniform3f(prog.uLightDir, 0.55f, 0.85f, 0.45f)
        GLES20.glUniform3f(prog.uSkyColor, 0.35f, 0.55f, 0.85f)
        GLES20.glUniform3f(prog.uGroundColor, 0.14f, 0.12f, 0.18f)
        GLES20.glUniform3f(prog.uFogColor, 0.035f, 0.047f, 0.071f)
        GLES20.glUniform1f(prog.uFogDensity, 0.025f)

        // Draw turntable floor & glowing rings
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(prog.uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(prog.uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(prog.uRoughness, 0.18f)
        GLES20.glUniform1f(prog.uMetallic, 0.35f)
        GLES20.glUniform1f(prog.uPearl, 0.0f)
        GLES20.glUniform1f(prog.uAlpha, 1.0f)
        floorMesh?.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
        ringMesh?.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

        val car = carMeshes ?: return
        val setup = activeSetup
        val finish = setup?.finishEnum ?: com.example.model.PaintFinish.METALLIC

        // Draw Underglow halo
        car.underglowMesh?.let { glow ->
            GLES20.glUniform1f(prog.uAlpha, 0.45f)
            glow.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
            GLES20.glUniform1f(prog.uAlpha, 1.0f)
        }

        // Draw Painted Car Body with finish material
        GLES20.glUniform1f(prog.uRoughness, finish.roughness)
        GLES20.glUniform1f(prog.uMetallic, finish.metallic)
        GLES20.glUniform1f(prog.uPearl, finish.pearl)
        car.bodyMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

        // Draw Carbon / Glass / Lights Trim
        GLES20.glUniform1f(prog.uRoughness, 0.12f)
        GLES20.glUniform1f(prog.uMetallic, 0.45f)
        GLES20.glUniform1f(prog.uPearl, 0.0f)
        car.trimMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

        // Draw Spoiler
        GLES20.glUniform1f(prog.uRoughness, finish.roughness)
        GLES20.glUniform1f(prog.uMetallic, finish.metallic)
        car.spoilerMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)

        // Draw 4 Wheels
        GLES20.glUniform1f(prog.uRoughness, 0.22f)
        GLES20.glUniform1f(prog.uMetallic, 0.85f)
        car.wheelPositions.forEach { wPos ->
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, wPos.x, wPos.y, wPos.z)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(prog.uModelMatrix, 1, false, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(prog.uMvpMatrix, 1, false, mvpMatrix, 0)
            car.wheelMesh.draw(prog.aPosition, prog.aNormal, prog.aColor, prog.aEmissive)
        }
    }
}

class GarageGLSurfaceView(context: Context) : GLSurfaceView(context) {
    val renderer = GarageRenderer()
    private var prevX = 0f
    private var prevY = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                prevX = event.x
                prevY = event.y
                renderer.autoRotate = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - prevX
                val dy = event.y - prevY
                renderer.orbitYawDeg -= dx * 0.45f
                renderer.orbitPitchDeg = (renderer.orbitPitchDeg + dy * 0.25f).coerceIn(5f, 45f)
                prevX = event.x
                prevY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}

@Composable
fun GarageShowcaseView(
    carSpec: CarSpec,
    carSetup: CarSetup,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glView = remember(context) { GarageGLSurfaceView(context) }

    LaunchedEffect(carSpec, carSetup) {
        glView.renderer.setCar(carSpec, carSetup)
    }

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
