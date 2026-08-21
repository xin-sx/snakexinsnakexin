package com.demo.snakexin

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * 视频全屏播放。
 * - 打开时强制横屏（sensorLandscape），并进入沉浸式全屏。
 * - 传入的是媒体文件的绝对路径（应用私有目录下，可直接用 file:// URI 加载）。
 * - Activity 销毁时释放 ExoPlayer。
 */
class VideoFullscreenActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 强制横屏：避免用户竖屏进入后再翻转造成重启
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // 全屏沉浸式
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersive()

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            controllerHideOnTouch = true
            setShowFastForwardButton(true)
            setShowRewindButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        setContentView(playerView)

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrEmpty()) {
            finish()
            return
        }
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) {
            finish()
            return
        }

        val p = ExoPlayer.Builder(this).build()
        player = p
        playerView.player = p
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.prepare()
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // 播放完自动退出
                    finish()
                }
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { c ->
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.let {
            it.stop()
            it.release()
        }
        player = null
    }

    companion object {
        private const val EXTRA_PATH = "extra_video_path"
        fun newIntent(ctx: Context, absolutePath: String): Intent {
            return Intent(ctx, VideoFullscreenActivity::class.java).apply {
                putExtra(EXTRA_PATH, absolutePath)
            }
        }
    }
}
