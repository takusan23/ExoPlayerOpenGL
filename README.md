# ExoPlayer OpenGL Sample
ExoPlayer の OpenGL のサンプルを動かしてみるものです。  
映像の上に Canvas を重ねてみたものです。

![Imgur](https://imgur.com/xbgZyFY.png)

# see also
https://github.com/google/ExoPlayer/tree/release-v2/demos/gl

# メモ
映像の上にCanvasを重ねる。二回図形を描画することで多分作れる。  
詳しくは BitmapOverlayVideoProcessor 参照。

- まず 映像 を描画する
  - SurfaceTexture とか使う
  - アスペクト比を合わせるために Matrix.scaleM などを使い、 gl_Position の値を調整する
- 次に Canvas を描画する
  - Canvas を Bitmap にして、テクスチャを登録する
  - 別に図形を描画することになるので、アルファブレンドとかの設定をしておく
  - Canvas のサイズを GLSurfaceView に合わせているため、gl_Position でアスペクト比の調整をしていた場合はリセットしておく