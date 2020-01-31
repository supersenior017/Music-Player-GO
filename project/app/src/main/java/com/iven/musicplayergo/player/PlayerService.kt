package com.iven.musicplayergo.player

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcelable
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import com.iven.musicplayergo.R
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.toColouredToast


class PlayerService : Service() {

    // Binder given to clients
    private val binder = LocalBinder()

    // Check if is already running
    var isRunning = false

    //media player
    lateinit var mediaPlayerHolder: MediaPlayerHolder
    lateinit var musicNotificationManager: MusicNotificationManager
    var isRestoredFromPause = false

    private lateinit var mMediaSessionCompat: MediaSessionCompat

    private val mMediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            mediaPlayerHolder.seekTo(pos.toInt())
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?) =
            handleMediaIntent(mediaButtonEvent)
    }

    private fun configureMediaSession() {
        mMediaSessionCompat = MediaSessionCompat(this, packageName).apply {
            isActive = true
            setCallback(mMediaSessionCallback)
        }
    }

    fun getMediaSession(): MediaSessionCompat {
        return mMediaSessionCompat
    }

    override fun onDestroy() {
        super.onDestroy()

        isRunning = false

        if (::mediaPlayerHolder.isInitialized) {
            //saves last played song and its position
            if (mediaPlayerHolder.isCurrentSong) mediaPlayerHolder.apply {
                goPreferences.latestPlayedSong =
                    Triple(currentSong.first, playerPosition, isPlayingFromFolder)
            }

            goPreferences.latestVolume = mediaPlayerHolder.currentVolumeInPercent

            mediaPlayerHolder.release()
        }

        if (::mMediaSessionCompat.isInitialized && mMediaSessionCompat.isActive) {
            mMediaSessionCompat.isActive = false
            mMediaSessionCompat.setCallback(null)
            mMediaSessionCompat.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /*This mode makes sense for things that will be explicitly started
        and stopped to run for arbitrary periods of time, such as a service
        performing background music playback.*/
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        if (!::mediaPlayerHolder.isInitialized) {
            mediaPlayerHolder = MediaPlayerHolder(this).apply {
                registerActionsReceiver()
            }
            musicNotificationManager = MusicNotificationManager(this)
        }
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        configureMediaSession()
    }

    inner class LocalBinder : Binder() {
        // Return this instance of PlayerService so we can call public methods
        fun getService() = this@PlayerService
    }

    private fun handleMediaIntent(intent: Intent?): Boolean {

        var isSuccess = false

        try {
            intent?.let {
                val event =
                    intent.getParcelableExtra<Parcelable>(Intent.EXTRA_KEY_EVENT) as KeyEvent
                if (event.action == KeyEvent.ACTION_DOWN) { // do something
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                            mediaPlayerHolder.resumeOrPause()
                            isSuccess = true
                        }
                        KeyEvent.KEYCODE_MEDIA_CLOSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                            mediaPlayerHolder.stopPlaybackService(true)
                            isSuccess = true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            mediaPlayerHolder.skip(false)
                            isSuccess = true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            mediaPlayerHolder.skip(true)
                            isSuccess = true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            mediaPlayerHolder.repeatSong()
                            isSuccess = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isSuccess = false
            getString(R.string.error_media_buttons).toColouredToast(this)
            e.printStackTrace()
        }

        return isSuccess
    }
}
