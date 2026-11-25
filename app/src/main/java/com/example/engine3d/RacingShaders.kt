package com.example.engine3d

import android.opengl.GLES20
import android.util.Log

class RacingShaderProgram(vertexSrc: String, fragmentSrc: String) {
    val programId: Int

    val uMvpMatrix: Int
    val uModelMatrix: Int
    val uCameraPos: Int
    val uLightDir: Int
    val uSkyColor: Int
    val uGroundColor: Int
    val uFogColor: Int
    val uFogDensity: Int
    val uRoughness: Int
    val uMetallic: Int
    val uPearl: Int
    val uAlpha: Int

    val aPosition: Int
    val aNormal: Int
    val aColor: Int
    val aEmissive: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            val status = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("RacingShader", "Link error: ${GLES20.glGetProgramInfoLog(prog)}")
            }
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        uMvpMatrix = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrix = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uCameraPos = GLES20.glGetUniformLocation(programId, "uCameraPos")
        uLightDir = GLES20.glGetUniformLocation(programId, "uLightDir")
        uSkyColor = GLES20.glGetUniformLocation(programId, "uSkyColor")
        uGroundColor = GLES20.glGetUniformLocation(programId, "uGroundColor")
        uFogColor = GLES20.glGetUniformLocation(programId, "uFogColor")
        uFogDensity = GLES20.glGetUniformLocation(programId, "uFogDensity")
        uRoughness = GLES20.glGetUniformLocation(programId, "uRoughness")
        uMetallic = GLES20.glGetUniformLocation(programId, "uMetallic")
        uPearl = GLES20.glGetUniformLocation(programId, "uPearl")
        uAlpha = GLES20.glGetUniformLocation(programId, "uAlpha")

        aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormal = GLES20.glGetAttribLocation(programId, "aNormal")
        aColor = GLES20.glGetAttribLocation(programId, "aColor")
        aEmissive = GLES20.glGetAttribLocation(programId, "aEmissive")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    fun release() {
        GLES20.glDeleteProgram(programId)
    }

    companion object {
        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("RacingShader", "Compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            }
            return shader
        }

        const val PBR_VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            attribute float aEmissive;

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec3 vColor;
            varying float vEmissive;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize(mat3(uModelMatrix) * aNormal);
                vColor = aColor;
                vEmissive = aEmissive;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        const val PBR_FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec3 uCameraPos;
            uniform vec3 uLightDir;
            uniform vec3 uSkyColor;
            uniform vec3 uGroundColor;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uRoughness;
            uniform float uMetallic;
            uniform float uPearl;
            uniform float uAlpha;

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec3 vColor;
            varying float vEmissive;

            void main() {
                vec3 N = normalize(vWorldNormal);
                vec3 V = normalize(uCameraPos - vWorldPos);
                vec3 L = normalize(uLightDir);
                vec3 H = normalize(L + V);

                // Hemisphere ambient lighting
                float hemi = N.y * 0.5 + 0.5;
                vec3 ambient = mix(uGroundColor, uSkyColor, hemi) * 0.65;

                // Key directional diffuse + soft fill light
                float ndotl = max(dot(N, L), 0.0);
                vec3 fillDir = normalize(vec3(-L.x, 0.4, -L.z));
                float fillDiff = max(dot(N, fillDir), 0.0) * 0.25;
                vec3 diffuse = vColor * (ambient + ndotl * 0.85 + fillDiff);

                // Blinn-Phong clearcoat & metallic specular
                float shininess = mix(128.0, 14.0, clamp(uRoughness, 0.05, 1.0));
                float specPower = pow(max(dot(N, H), 0.0), shininess);
                vec3 specTint = mix(vec3(1.0), vColor, uMetallic * 0.65);
                float specStrength = mix(0.85, 0.18, uRoughness);
                vec3 specular = specTint * specPower * specStrength;

                // Fresnel sky reflection + pearlescent sheen
                float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
                vec3 envReflect = mix(uGroundColor * 0.4, uSkyColor * 1.35, clamp(reflect(-V, N).y * 0.5 + 0.5, 0.0, 1.0));
                vec3 pearlShift = vec3(0.2, 0.85, 1.0) * fresnel * uPearl * 0.45;
                vec3 reflection = envReflect * fresnel * mix(0.65, 0.12, uRoughness) + pearlShift;

                // Emissive override (headlights, taillights, neon strips)
                vec3 litColor = diffuse + specular + reflection;
                vec3 finalColor = mix(litColor, vColor * (1.25 + vEmissive * 0.8), clamp(vEmissive, 0.0, 1.0));

                // Exponential distance fog
                float dist = length(uCameraPos - vWorldPos);
                float fogFactor = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
                finalColor = mix(finalColor, uFogColor, clamp(fogFactor, 0.0, 1.0));

                gl_FragColor = vec4(finalColor, uAlpha);
            }
        """

        const val SKY_VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aColor;
            varying vec3 vColor;
            void main() {
                vColor = aColor;
                gl_Position = vec4(aPosition, 1.0);
            }
        """

        const val SKY_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 vColor;
            void main() {
                gl_FragColor = vec4(vColor, 1.0);
            }
        """
    }
}
