package com.xiaozhi

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VideoPlayerManager(private val context: Context, initialPlayerView: PlayerView? = null) {

    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var onPlaybackEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // Public property để MainActivity có thể truy cập
    var playerView: PlayerView? = initialPlayerView
        private set

    fun attachPlayerView(view: PlayerView) {
        playerView = view
        // Nếu player đã tồn tại, gán nó vào view mới
        player?.let {
            view.player = it
            view.visibility = if (it.isPlaying) View.VISIBLE else View.GONE
        } ?: run {
            // Nếu chưa có player, khởi tạo và gán
            initialize()
        }
    }

    fun initialize() {
        if (player != null) return
        mainHandler.post {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_ENDED -> mainHandler.post { onPlaybackEnded?.invoke() }
                            Player.STATE_IDLE -> {}
                            Player.STATE_BUFFERING -> {}
                            Player.STATE_READY -> {}
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        onError?.invoke(error.message ?: "Lỗi phát video")
                        stop()
                    }
                })
            }
            playerView?.player = player
        }
    }

    fun play(uri: Uri) {
        initialize()
        mainHandler.post {
            val mediaSource = when (uri.scheme?.lowercase()) {
                "rtsp" -> RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(uri))
                "http", "https" -> {
                    if (uri.toString().endsWith(".m3u8", ignoreCase = true)) {
                        val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(
                            OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build()
                        )
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(uri))
                    } else {
                        val dataSourceFactory = DefaultHttpDataSource.Factory()
                            .setUserAgent("XiaoZhi-Android/1.0")
                            .setConnectTimeoutMs(15000)
                            .setReadTimeoutMs(30000)
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(uri))
                    }
                }
                else -> {
                    val dataSourceFactory = DefaultHttpDataSource.Factory()
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri))
                }
            }
            player?.apply {
                setMediaSource(mediaSource)
                prepare()
                play()
            }
            playerView?.visibility = View.VISIBLE
        }
    }

    fun playUrl(url: String) = play(Uri.parse(url))

    fun stop() {
        mainHandler.post {
            player?.stop()
            player?.clearMediaItems()
            playerView?.visibility = View.GONE
        }
    }

    fun pause() = mainHandler.post { player?.pause() }
    fun resume() = mainHandler.post { player?.play() }

    fun release() {
        mainHandler.post {
            player?.release()
            player = null
            playerView?.player = null
        }
    }

    val isPlaying: Boolean
        get() = player?.isPlaying ?: false
}