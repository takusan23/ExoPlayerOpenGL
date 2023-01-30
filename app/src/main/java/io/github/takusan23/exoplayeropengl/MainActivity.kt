package io.github.takusan23.exoplayeropengl

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.VideoSize
import io.github.takusan23.exoplayeropengl.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val viewBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val exoPlayer by lazy { ExoPlayer.Builder(this).build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        val videoProcessingGLSurfaceView = VideoProcessingGLSurfaceView(this, false, BitmapOverlayVideoProcessor(this))
        viewBinding.playerParentFrameLayout.addView(videoProcessingGLSurfaceView)

        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.setMediaItem(MediaItem.fromUri(DEFAULT_MEDIA_URI))
        exoPlayer.prepare()
        exoPlayer.play()
        videoProcessingGLSurfaceView.setPlayer(exoPlayer)
        exoPlayer.addAnalyticsListener(EventLogger())

        exoPlayer.addListener(object : Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                super.onVideoSizeChanged(videoSize)
                println("height = " + videoSize.height)
                println("width = " + videoSize.width)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }

    companion object {
        private const val DEFAULT_MEDIA_URI = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
    }

}