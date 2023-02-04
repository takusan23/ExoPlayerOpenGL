package io.github.takusan23.exoplayeropengl

object ShaderCode {

    @JvmField
    val VERTEX_SHADER = """
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

    @JvmField
    // GLSL なので Android Graphic Shader Language ではないけど 無いよりマシなので
    // language=agsl
    val FRAGMENT_SHADER = """
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
        
        uniform int uDrawVideo;
        
        void main() {
          vec4 videoColor = texture2D(uTexSampler0, vec2(vTexCoords.x, vTexCoords.y));       
          vec4 overlayColor = texture2D(uTexSampler1, vec2(vTexCoords.x, vTexCoords.y));

          // 映像の描画 or テクスチャの描画
          if (bool(uDrawVideo)) {
            gl_FragColor = videoColor;
          } else {
            gl_FragColor = overlayColor;
          }
        }
        
    """

}