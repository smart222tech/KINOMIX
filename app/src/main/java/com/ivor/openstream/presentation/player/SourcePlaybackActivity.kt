package com.ivor.openstream.presentation.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@UnstableApi
class SourcePlaybackActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank() || !url.startsWith("https://") && !url.startsWith("http://")) {
            finish()
            return
        }
        val mimeType = intent.getStringExtra(EXTRA_MIME).orEmpty()
        val referer = intent.getStringExtra(EXTRA_REFERER).orEmpty()
        val headers = if (referer.isBlank()) emptyMap() else mapOf("Referer" to referer)
        val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = exoPlayer
        val view = PlayerView(this).apply {
            player = exoPlayer
            useController = true
            controllerAutoShow = true
        }
        setContentView(view)
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (mimeType.isNotBlank()) setMimeType(mimeType) }
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_URL = "source_playback_url"
        const val EXTRA_MIME = "source_playback_mime"
        const val EXTRA_REFERER = "source_playback_referer"
        const val EXTRA_TITLE = "source_playback_title"
    }
}
