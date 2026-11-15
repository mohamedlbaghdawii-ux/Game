package com.example.game.render

import android.opengl.GLES20

/**
 * Compiles and manages OpenGL ES 2.0 shaders for 3D lit geometry and atmospheric sky gradients.
 */
class LitShaderProgram {

    val programId: Int
    val aPosLoc: Int
    val aNormalLoc: Int
    val uMvpLoc: Int
    val uModelLoc: Int
    val uColorLoc: Int
    val uEmissiveLoc: Int
    val uLightDirLoc: Int
    val uFogColorLoc: Int
    val uFogAmountLoc: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        aPosLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
        uMvpLoc = GLES20.glGetUniformLocation(programId, "uMVP")
        uModelLoc = GLES20.glGetUniformLocation(programId, "uModel")
        uColorLoc = GLES20.glGetUniformLocation(programId, "uColor")
        uEmissiveLoc = GLES20.glGetUniformLocation(programId, "uEmissive")
        uLightDirLoc = GLES20.glGetUniformLocation(programId, "uLightDir")
        uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
        uFogAmountLoc = GLES20.glGetUniformLocation(programId, "uFogAmount")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            void main() {
                vec4 worldPos = uModel * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uEmissive;
            uniform vec3 uLightDir;
            uniform vec3 uFogColor;
            uniform float uFogAmount;
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            void main() {
                vec3 n = normalize(vWorldNormal);
                vec3 l = normalize(uLightDir);
                float ndotl = max(dot(n, l), 0.0);

                // Hemisphere ambient (warm sky top, cool steel bounce bottom)
                float hemi = n.y * 0.5 + 0.5;
                vec3 ambient = mix(vec3(0.22, 0.25, 0.32), vec3(0.50, 0.54, 0.62), hemi);

                // Crisp directional sun + subtle specular rim
                vec3 sunCol = vec3(1.05, 0.96, 0.84) * ndotl * 0.72;
                vec3 viewDir = normalize(vec3(0.0, 0.15, 1.0));
                vec3 halfVec = normalize(l + viewDir);
                float spec = pow(max(dot(n, halfVec), 0.0), 22.0) * 0.22;

                vec3 lit = uColor.rgb * (ambient + sunCol) + vec3(spec) + uEmissive;
                vec3 finalRgb = mix(lit, uFogColor, clamp(uFogAmount, 0.0, 1.0));
                gl_FragColor = vec4(finalRgb, uColor.a);
            }
        """
    }
}

class SkyShaderProgram {
    val programId: Int
    val aPosLoc: Int
    val uTopColorLoc: Int
    val uMidColorLoc: Int
    val uBotColorLoc: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, SKY_VS)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, SKY_FS)
        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        aPosLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        uTopColorLoc = GLES20.glGetUniformLocation(programId, "uTopColor")
        uMidColorLoc = GLES20.glGetUniformLocation(programId, "uMidColor")
        uBotColorLoc = GLES20.glGetUniformLocation(programId, "uBotColor")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    companion object {
        private const val SKY_VS = """
            attribute vec2 aPosition;
            varying float vY;
            void main() {
                vY = aPosition.y * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.999, 1.0);
            }
        """

        private const val SKY_FS = """
            precision mediump float;
            uniform vec3 uTopColor;
            uniform vec3 uMidColor;
            uniform vec3 uBotColor;
            varying float vY;
            void main() {
                vec3 c = vY > 0.45
                    ? mix(uMidColor, uTopColor, (vY - 0.45) / 0.55)
                    : mix(uBotColor, uMidColor, vY / 0.45);
                gl_FragColor = vec4(c, 1.0);
            }
        """
    }
}

private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    return shader
}
