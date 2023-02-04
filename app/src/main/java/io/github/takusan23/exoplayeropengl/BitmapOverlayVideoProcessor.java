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
package io.github.takusan23.exoplayeropengl;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableKt;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;

import java.util.Locale;

import javax.microedition.khronos.opengles.GL10;

/**
 * Video processor that demonstrates how to overlay a bitmap on video output using a GL shader. The
 * bitmap is drawn using an Android {@link Canvas}.
 */
/* package */ final class BitmapOverlayVideoProcessor
        implements VideoProcessingGLSurfaceView.VideoProcessor {

    private static final String TAG = "BitmapOverlayVP";
    private final Context context;
    private final Paint paint;
    private final int[] textures;
    private final Bitmap overlayBitmap;
    private final Bitmap logoBitmap;
    private final Canvas overlayCanvas;

    private GlProgram program;

    public BitmapOverlayVideoProcessor(Context context, int canvasHeight, int canvasWidth) {
        this.context = context.getApplicationContext();
        paint = new Paint();
        paint.setTextSize(64);
        paint.setAntiAlias(true);
        paint.setARGB(0xFF, 0xFF, 0xFF, 0xFF);
        textures = new int[1];
        overlayBitmap = Bitmap.createBitmap(canvasHeight, canvasWidth, Bitmap.Config.ARGB_8888);
        overlayCanvas = new Canvas(overlayBitmap);
        try {
            logoBitmap = DrawableKt.toBitmap(ContextCompat.getDrawable(context, R.drawable.baseline_coronavirus_24), 300, 300, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void initialize() {
        try {
            program = new GlProgram(ShaderCode.VERTEX_SHADER, ShaderCode.FRAGMENT_SHADER);
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to initialize the shader program", e);
            return;
        }
        program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

        program.setBufferAttribute("aTexCoords",
                GlUtil.getTextureCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, /* level= */ 0, overlayBitmap, /* border= */ 0);

        // アルファブレンド を有効
        // これにより、透明なテクスチャがちゃんと透明に描画される
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void setSurfaceSize(int width, int height) {
        System.out.println(width);
        // bitmapScaleX = (float) width / OVERLAY_WIDTH;
        // bitmapScaleY = (float) height / OVERLAY_HEIGHT;
    }

    @Override
    public void draw(int frameTexture, long frameTimestampUs, float[] transformMatrix) {
        // Draw to the canvas and store it in a texture.
        String text = String.format(Locale.US, "%.02f", frameTimestampUs / (float) C.MICROS_PER_SECOND);
        overlayBitmap.eraseColor(Color.TRANSPARENT);
        // overlayCanvas.drawBitmap(logoBitmap, /* left= */ 32, /* top= */ 32, paint);
        overlayCanvas.drawText(text, /* x= */ 200, /* y= */ 130, paint);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
        GLUtils.texSubImage2D(GL10.GL_TEXTURE_2D, /* level= */ 0, /* xoffset= */ 0, /* yoffset= */ 0, overlayBitmap);
        try {
            GlUtil.checkGlError();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to populate the texture", e);
        }

        // Run the shader program.
        GlProgram program = checkNotNull(this.program);
        program.setSamplerTexIdUniform("uTexSampler0", frameTexture, /* texUnitIndex= */ 0);
        program.setSamplerTexIdUniform("uTexSampler1", textures[0], /* texUnitIndex= */ 1);
        // program.setFloatUniform("uScaleX", bitmapScaleX);
        // program.setFloatUniform("uScaleY", bitmapScaleY);


        program.setFloatsUniform("uTexTransform", transformMatrix);

        // --------------------------
        // まず映像を描画する。映像を描画するフラグを立てます
        // --------------------------
        program.setIntUniform("uDrawVideo", 1);
        // アスペクト比を調整して gl_Position を調整する
        float[] scaleTransform = GlUtil.getNormalizedCoordinateBounds();
        Matrix.setIdentityM(scaleTransform, 0);
        Matrix.scaleM(scaleTransform, 0, 1f, .56f, 1f);
        program.setFloatsUniform("scaleTransform", scaleTransform);

        try {
            program.bindAttributesAndUniforms();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to update the shader program", e);
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
        try {
            GlUtil.checkGlError();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to draw a frame", e);
        }

        // --------------------------
        // 次に Canvas を描画する。フラグを下ろす
        // --------------------------
        program.setIntUniform("uDrawVideo", 0);
        // Scale の調整を戻す
        Matrix.setIdentityM(scaleTransform, 0);
        program.setFloatsUniform("scaleTransform", scaleTransform);
        try {
            program.bindAttributesAndUniforms();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to update the shader program", e);
        }
        // GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
        try {
            GlUtil.checkGlError();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to draw a frame", e);
        }

    }

    @Override
    public void release() {
        if (program != null) {
            try {
                program.delete();
            } catch (GlUtil.GlException e) {
                Log.e(TAG, "Failed to delete the shader program", e);
            }
        }
    }
}
