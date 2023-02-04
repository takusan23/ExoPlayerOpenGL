/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.takusan23.exoplayeropengl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.GlProgram
import com.google.android.exoplayer2.util.GlUtil
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.Log
import io.github.takusan23.exoplayeropengl.VideoProcessingGLSurfaceView.VideoProcessor
import java.util.*
import javax.microedition.khronos.opengles.GL10

/**
 * 映像の上に Canvas を重ねて再生する
 *
 * @param context [Context]
 * @param canvasWidth キャンバスの幅
 * @param canvasHeight キャンバスの高さ
 */
class BitmapOverlayVideoProcessor(
    private val context: Context,
    private val canvasHeight: Int,
    private val canvasWidth: Int
) : VideoProcessor {

    private val textures = IntArray(1)
    private val overlayBitmap = Bitmap.createBitmap(canvasHeight, canvasWidth, Bitmap.Config.ARGB_8888)
    private val overlayCanvas = android.graphics.Canvas(overlayBitmap)
    private val logoBitmap by lazy {
        ContextCompat.getDrawable(context, R.drawable.outline_android_24)!!.apply {
            setTint(Color.WHITE)
        }.toBitmap(300, 300)
    }
    private var program: GlProgram? = null

    private val paint = Paint().apply {
        textSize = 64f
        isAntiAlias = true
        color = Color.WHITE
    }

    override fun initialize() {
        val program = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: GlException) {
            Log.e(TAG, "Failed to initialize the shader program", e)
            return
        }
        this@BitmapOverlayVideoProcessor.program = program

        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )

        program.setBufferAttribute(
            "aTexCoords",
            GlUtil.getTextureCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )

        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE.toFloat())
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, overlayBitmap, 0)

        // アルファブレンドを有効
        // これにより、透明なテクスチャがちゃんと透明に描画される
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        // do nothing
    }

    override fun draw(frameTexture: Int, frameTimestampUs: Long, transformMatrix: FloatArray) {
        // Draw to the canvas and store it in a texture.
        val text = String.format(Locale.US, "%.02f", frameTimestampUs / C.MICROS_PER_SECOND.toFloat())
        overlayBitmap.eraseColor(Color.TRANSPARENT)
        overlayCanvas.drawBitmap(logoBitmap, 1100f, 1100f, paint)
        overlayCanvas.drawText(text, 200f, 130f, paint)
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])
        GLUtils.texSubImage2D(GL10.GL_TEXTURE_2D, 0, 0, 0, overlayBitmap)
        try {
            GlUtil.checkGlError()
        } catch (e: GlException) {
            Log.e(TAG, "Failed to populate the texture", e)
        }

        // Run the shader program.
        val program = Assertions.checkNotNull(program)
        program.setSamplerTexIdUniform("uTexSampler0", frameTexture, 0)
        program.setSamplerTexIdUniform("uTexSampler1", textures[0], 1)
        program.setFloatsUniform("uTexTransform", transformMatrix)

        // --------------------------
        // まず映像を描画する。映像を描画するフラグを立てます
        // --------------------------
        program.setIntUniform("uDrawVideo", 1)
        // アスペクト比を調整して gl_Position を調整する
        // 0.56f は各自調節して 多分 16:9 なら使い回せる
        val scaleTransform = GlUtil.getNormalizedCoordinateBounds()
        Matrix.setIdentityM(scaleTransform, 0)
        Matrix.scaleM(scaleTransform, 0, 1f, .56f, 1f)
        program.setFloatsUniform("scaleTransform", scaleTransform)
        try {
            program.bindAttributesAndUniforms()
        } catch (e: GlException) {
            Log.e(TAG, "Failed to update the shader program", e)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        try {
            GlUtil.checkGlError()
        } catch (e: GlException) {
            Log.e(TAG, "Failed to draw a frame", e)
        }

        // --------------------------
        // 次に Canvas を描画する。フラグを下ろす
        // --------------------------
        program.setIntUniform("uDrawVideo", 0)
        // Scale の調整を戻す
        // Scale の調整を戻す
        Matrix.setIdentityM(scaleTransform, 0)
        program.setFloatsUniform("scaleTransform", scaleTransform)
        try {
            program.bindAttributesAndUniforms()
        } catch (e: GlException) {
            Log.e(TAG, "Failed to update the shader program", e)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        try {
            GlUtil.checkGlError()
        } catch (e: GlException) {
            Log.e(TAG, "Failed to draw a frame", e)
        }

    }

    override fun release() {
        try {
            program?.delete()
            logoBitmap.recycle()
        } catch (e: GlException) {
            Log.e(TAG, "Failed to delete the shader program", e)
        }
    }

    companion object {

        private const val VERTEX_SHADER = """
        // Copyright 2020 The Android Open Source Project
        //
        // Licensed under the Apache License, Version 2.0 (the "License");
        // you may not use this file except in compliance with the License.
        // You may obtain a copy of the License at
        //
        //      http://www.apache.org/licenses/LICENSE-2.0
        //
        // Unless required by applicable law or agreed to in writing, software
        // distributed under the License is distributed on an "AS IS" BASIS,
        // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        // See the License for the specific language governing permissions and
        // limitations under the License.
        
        attribute vec4 aFramePosition;
        attribute vec4 aTexCoords;
        uniform mat4 uTexTransform;
        uniform mat4 scaleTransform;
        varying vec2 vTexCoords;
        
        void main() {
         gl_Position = aFramePosition * scaleTransform;
         vTexCoords = (uTexTransform * aTexCoords).xy;
        }
    """

        private const val FRAGMENT_SHADER = """
        // Copyright 2020 The Android Open Source Project
        //
        // Licensed under the Apache License, Version 2.0 (the "License");
        // you may not use this file except in compliance with the License.
        // You may obtain a copy of the License at
        //
        //      http://www.apache.org/licenses/LICENSE-2.0
        //
        // Unless required by applicable law or agreed to in writing, software
        // distributed under the License is distributed on an "AS IS" BASIS,
        // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        // See the License for the specific language governing permissions and
        // limitations under the License.
        
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        // External texture containing video decoder output.
        uniform samplerExternalOES uTexSampler0;
        // Texture containing the overlap bitmap.
        uniform sampler2D uTexSampler1;
        // Horizontal scaling factor for the overlap bitmap.
        // uniform float uScaleX;
        // Vertical scaling factory for the overlap bitmap.
        // uniform float uScaleY;
        varying vec2 vTexCoords;
        // 1 = draw video / 0 = draw canvas 
        uniform int uDrawVideo;
        
        void main() {
          vec4 videoColor = texture2D(uTexSampler0, vec2(vTexCoords.x, vTexCoords.y));       
          vec4 overlayColor = texture2D(uTexSampler1, vec2(vTexCoords.x, vTexCoords.y));

          // 映像の描画 or テクスチャの描画
          // アルファブレンドの設定をしておく必要あり
          if (bool(uDrawVideo)) {
            gl_FragColor = videoColor;
          } else {
            gl_FragColor = overlayColor;
          }
        }
    """

        private val TAG = BitmapOverlayVideoProcessor::class.java.simpleName
    }
}