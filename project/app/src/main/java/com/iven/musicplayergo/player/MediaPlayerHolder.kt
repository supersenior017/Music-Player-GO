package com.iven.musicplayergo.player

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import com.iven.musicplayergo.MainActivity
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.music.Music
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.ln

/**
 * Exposes the functionality of the [MediaPlayer]
 */

// The volume we set the media player to when we lose audio focus, but are
// allowed to reduce the volume instead of stopping playback.
private const val VOLUME_DUCK = 0.2f
// The volume we set the media player when we have audio focus.
private const val VOLUME_NORMAL = 1.0f
// we don't have audio focus, and can't duck (play at a low volume)
private const val AUDIO_NO_FOCUS_NO_DUCK = 0
// we don't have focus, but can duck (play at a low volume)
private const val AUDIO_NO_FOCUS_CAN_DUCK = 1
// we have full audio focus
private const val AUDIO_FOCUSED = 2

// The headset connection states (0,1)
private const val HEADSET_DISCONNECTED = 0
private const val HEADSET_CONNECTED = 1

// Player playing statuses
const val PLAYING = 0
const val PAUSED = 1
const val RESUMED = 2

@Suppress("DEPRECATION")
class MediaPlayerHolder(private val playerService: PlayerService) :
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener {

    lateinit var mediaPlayerInterface: MediaPlayerInterface

    //audio focus
    private var mAudioManager = playerService.getSystemService(AUDIO_SERVICE) as AudioManager
    private lateinit var mAudioFocusRequestOreo: AudioFocusRequest
    private val mHandler = Handler()

    private var mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private var sPlayOnFocusGain: Boolean = false

    private val mOnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> mCurrentAudioFocusState = AUDIO_FOCUSED
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                    // Audio focus was lost, but it's possible to duck (i.e.: play quietly)
                    mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Lost audio focus, but will gain it back (shortly), so note whether
                    // playback should resume
                    mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                    sPlayOnFocusGain = isMediaPlayer && state == PLAYING || state == RESUMED
                }
                AudioManager.AUDIOFOCUS_LOSS ->
                    // Lost audio focus, probably "permanently"
                    mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
            }
            // Update the player state based on the change
            if (isMediaPlayer && goPreferences.isFocusEnabled) configurePlayerState()
        }

    //media player
    private var mediaPlayer: MediaPlayer? = null
    private var mExecutor: ScheduledExecutorService? = null
    private var mSeekBarPositionUpdateTask: Runnable? = null

    //first: current song, second: isFromQueue
    var currentSong: Pair<Music, Boolean>? = null
    private lateinit var mPlayingAlbumSongs: List<Music>

    var currentVolumeInPercent = 100
    val playerPosition: Int get() = if (!isMediaPlayer) goPreferences.lastPlayedSong?.second!! else mediaPlayer!!.currentPosition

    //media player state/booleans
    val isPlaying: Boolean get() = isMediaPlayer && mediaPlayer!!.isPlaying
    val isMediaPlayer: Boolean get() = mediaPlayer != null
    var isReset = false

    var isQueue = false
    var isQueueStarted = false
    private lateinit var preQueueSong: Pair<Music, List<Music>>
    var queueSongs = mutableListOf<Music>()

    var isSongRestoredFromPrefs = false
    var isSongFromLovedSongs = Pair(false, 0)

    var state: Int? = PAUSED
    var isPlay = false

    //notifications
    private var mNotificationActionsReceiver: NotificationReceiver? = null
    private lateinit var mMusicNotificationManager: MusicNotificationManager

    private fun registerActionsReceiver() {
        mNotificationActionsReceiver = NotificationReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(PREV_ACTION)
        intentFilter.addAction(PLAY_PAUSE_ACTION)
        intentFilter.addAction(NEXT_ACTION)
        intentFilter.addAction(CLOSE_ACTION)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        playerService.registerReceiver(mNotificationActionsReceiver, intentFilter)
    }

    private fun unregisterActionsReceiver() {
        if (mNotificationActionsReceiver != null) {
            try {
                playerService.unregisterReceiver(mNotificationActionsReceiver)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    fun registerNotificationActionsReceiver(isReceiver: Boolean) {
        if (isReceiver) registerActionsReceiver() else unregisterActionsReceiver()
    }

    fun setCurrentSong(song: Music, songs: List<Music>, isFromQueue: Boolean) {
        currentSong = Pair(song, isFromQueue)
        mPlayingAlbumSongs = songs
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {

        mediaPlayerInterface.onStateChanged()
        mediaPlayerInterface.onPlaybackCompleted()

        when {
            isReset -> if (isMediaPlayer) resetSong()
            isQueue -> manageQueue(true)
            else -> skip(true)
        }
    }

    fun onResumeActivity() {
        startUpdatingCallbackWithPosition()
    }

    fun onPauseActivity() {
        stopUpdatingCallbackWithPosition()
    }

    private fun tryToGetAudioFocus() {
        mCurrentAudioFocusState = when (getAudioFocusResult()) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> AUDIO_FOCUSED
            else -> AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    private fun getAudioFocusResult(): Int {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioFocusRequestOreo = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                })
                setOnAudioFocusChangeListener(mOnAudioFocusChangeListener, mHandler)
                build()
            }
            mAudioManager.requestAudioFocus(mAudioFocusRequestOreo)
        } else {
            mAudioManager.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun giveUpAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mAudioManager.abandonAudioFocusRequest(
            mAudioFocusRequestOreo
        )
        else
            mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener)
        mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    }

    private fun setStatus(status: Int) {
        state = status
        mediaPlayerInterface.onStateChanged()
    }

    private fun resumeMediaPlayer() {
        if (!isPlaying) {
            if (isMediaPlayer) mediaPlayer!!.start()
            setStatus(if (isSongRestoredFromPrefs) PLAYING else RESUMED)

            playerService.startForeground(
                NOTIFICATION_ID,
                mMusicNotificationManager.createNotification()
            )

            isSongRestoredFromPrefs = false
            if (!isPlay) isPlay = true
        }
    }

    private fun pauseMediaPlayer() {
        setStatus(PAUSED)
        mediaPlayer!!.pause()
        playerService.stopForeground(false)
        mMusicNotificationManager.notificationManager
            .notify(NOTIFICATION_ID, mMusicNotificationManager.createNotification())
    }

    private fun resetSong() {
        isReset = false
        mediaPlayer!!.seekTo(0)
        mediaPlayer!!.start()
        setStatus(PLAYING)
    }

    private fun manageQueue(isNext: Boolean) {

        if (isSongRestoredFromPrefs) isSongRestoredFromPrefs = false

        if (isQueueStarted) {
            currentSong = Pair(getSkipSong(isNext), true)
        } else {
            setCurrentSong(queueSongs[0], queueSongs, true)
            isQueueStarted = true
        }
        initMediaPlayer(currentSong?.first!!)

    }

    private fun getSkipSong(isNext: Boolean): Music {
        val currentIndex = mPlayingAlbumSongs.indexOf(currentSong?.first)
        val index: Int
        try {
            index = if (isNext) currentIndex + 1 else currentIndex - 1
            return mPlayingAlbumSongs[index]
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
            return if (isQueue) {

                if (isNext) {
                    setQueueEnabled(false)
                    setCurrentSong(preQueueSong.first, preQueueSong.second, false)
                    getSkipSong(true)
                } else {
                    preQueueSong.first
                }
            } else {
                if (currentIndex != 0) mPlayingAlbumSongs[0] else mPlayingAlbumSongs[mPlayingAlbumSongs.size - 1]
            }

        }
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private fun startUpdatingCallbackWithPosition() {

        if (mExecutor == null) mExecutor = Executors.newSingleThreadScheduledExecutor()
        if (mSeekBarPositionUpdateTask == null) mSeekBarPositionUpdateTask =
            Runnable { this.updateProgressCallbackTask() }

        mExecutor!!.scheduleAtFixedRate(
            mSeekBarPositionUpdateTask!!,
            0,
            1000,
            TimeUnit.MILLISECONDS
        )
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private fun stopUpdatingCallbackWithPosition() {
        if (mExecutor != null) {
            mExecutor!!.shutdownNow()
            mExecutor = null
            mSeekBarPositionUpdateTask = null
        }
    }

    private fun updateProgressCallbackTask() {
        if (isMediaPlayer && mediaPlayer!!.isPlaying) {
            val currentPosition = mediaPlayer!!.currentPosition
            mediaPlayerInterface.onPositionChanged(currentPosition)
        }
    }

    fun instantReset() {
        if (isMediaPlayer) {
            if (mediaPlayer!!.currentPosition < 5000) skip(false) else resetSong()
        }
    }

    /**
     * Once the [MediaPlayer] is released, it can't be used again, and another one has to be
     * created. In the onStop() method of the [MainActivity] the [MediaPlayer] is
     * released. Then in the onStart() of the [MainActivity] a new [MediaPlayer]
     * object has to be created. That's why this method is private, and called by load(int) and
     * not the constructor.
     */
    fun initMediaPlayer(song: Music) {

        try {
            if (isMediaPlayer) {
                mediaPlayer!!.reset()
            } else {
                mediaPlayer = MediaPlayer()
                EqualizerUtils.openAudioEffectSession(
                    playerService.applicationContext,
                    mediaPlayer!!.audioSessionId
                )

                mediaPlayer!!.setOnPreparedListener(this)
                mediaPlayer!!.setOnCompletionListener(this)
                mediaPlayer!!.setWakeMode(playerService, PowerManager.PARTIAL_WAKE_LOCK)
                mediaPlayer!!.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mMusicNotificationManager = playerService.musicNotificationManager
            }
            tryToGetAudioFocus()
            mediaPlayer!!.setDataSource(song.path)
            mediaPlayer!!.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        startUpdatingCallbackWithPosition()

        if (isReset) isReset = false

        if (isSongRestoredFromPrefs) {
            setPreciseVolume(currentVolumeInPercent)
            mediaPlayer.seekTo(goPreferences.lastPlayedSong?.second!!)
        } else if (isSongFromLovedSongs.first) {
            mediaPlayer.seekTo(isSongFromLovedSongs.second)
            isSongFromLovedSongs = Pair(false, 0)
        }

        if (isQueue) {
            mediaPlayerInterface.onQueueStartedOrEnded(true)
            isQueueStarted = true
        }

        if (isPlay) {
            setStatus(PLAYING)
            mediaPlayer.start()
            playerService.startForeground(
                NOTIFICATION_ID,
                mMusicNotificationManager.createNotification()
            )
        }
    }

    fun openEqualizer(activity: Activity) {
        EqualizerUtils.openEqualizer(activity, mediaPlayer!!)
    }

    fun release() {
        if (isMediaPlayer) {
            EqualizerUtils.closeAudioEffectSession(
                playerService.applicationContext,
                mediaPlayer!!.audioSessionId
            )
            mediaPlayer!!.release()
            mediaPlayer = null
            giveUpAudioFocus()
            unregisterActionsReceiver()
        }
    }

    fun resumeOrPause() {
        if (isPlaying) pauseMediaPlayer() else resumeMediaPlayer()
    }

    fun reset() {
        isReset = !isReset
    }

    fun setQueueEnabled(enabled: Boolean) {
        isQueue = enabled
        isQueueStarted = false

        if (isQueue) {
            preQueueSong = Pair(currentSong!!.first, mPlayingAlbumSongs)
            isQueue = true
            mediaPlayerInterface.onQueueEnabled()
        } else {
            queueSongs.clear()
            mediaPlayerInterface.onQueueCleared()
            mediaPlayerInterface.onQueueStartedOrEnded(false)
        }

    }

    fun skip(isNext: Boolean) {

        if (isQueue) {
            manageQueue(isNext)
        } else {
            currentSong = Pair(getSkipSong(isNext), false)
            initMediaPlayer(currentSong?.first!!)
        }
    }

    fun seekTo(position: Int) {
        if (isMediaPlayer) mediaPlayer?.seekTo(position)
    }

    /**
     * Reconfigures the player according to audio focus settings and starts/restarts it. This method
     * starts/restarts the MediaPlayer instance respecting the current audio focus state. So if we
     * have focus, it will play normally; if we don't have focus, it will either leave the player
     * paused or set it to a low volume, depending on what is permitted by the current focus
     * settings.
     */
    private fun configurePlayerState() {

        if (isMediaPlayer) {
            when (mCurrentAudioFocusState) {
                AUDIO_NO_FOCUS_NO_DUCK -> pauseMediaPlayer()
                else -> {
                    when (mCurrentAudioFocusState) {
                        AUDIO_NO_FOCUS_CAN_DUCK -> mediaPlayer?.setVolume(VOLUME_DUCK, VOLUME_DUCK)
                        else -> mediaPlayer?.setVolume(VOLUME_NORMAL, VOLUME_NORMAL)
                    }
                    // If we were playing when we lost focus, we need to resume playing.
                    if (sPlayOnFocusGain) {
                        resumeMediaPlayer()
                        sPlayOnFocusGain = false
                    }
                }
            }
        }
    }

    /* Sets the volume of the media player (scale is 1-100) */
    fun setPreciseVolume(percent: Int) {

        currentVolumeInPercent = percent

        if (isMediaPlayer) {
            fun volFromPercent(percent: Int): Float {
                if (percent == 100) return 1f
                return (1 - (ln((101 - percent).toFloat()) / ln(101f)))
            }

            val new = volFromPercent(percent)
            mediaPlayer!!.setVolume(new, new)
        }
    }

    fun stopPlaybackService(stopPlayback: Boolean) {
        if (playerService.isRunning && mediaPlayer != null && stopPlayback) {
            mediaPlayer!!.stop()
            val bindingIntent = Intent(playerService, PlayerService::class.java)
            playerService.stopService(bindingIntent)
            playerService.stopForeground(true)
            mediaPlayerInterface.onClose(true)
        }
        mediaPlayerInterface.onClose(true)
    }

    private inner class NotificationReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action != null) {
                when (action) {
                    PREV_ACTION -> instantReset()
                    PLAY_PAUSE_ACTION -> resumeOrPause()
                    NEXT_ACTION -> skip(true)
                    CLOSE_ACTION -> if (playerService.isRunning && mediaPlayer != null) stopPlaybackService(
                        true
                    )

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) resumeMediaPlayer()

                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED ->
                        when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) resumeMediaPlayer()
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                        }

                    Intent.ACTION_HEADSET_PLUG -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) {
                        when (intent.getIntExtra("state", -1)) {
                            //0 means disconnected
                            HEADSET_DISCONNECTED -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                            //1 means connected
                            HEADSET_CONNECTED -> if (currentSong != null && goPreferences.isHeadsetPlugEnabled) resumeMediaPlayer()
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> if (isPlaying && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                }
            }
        }
    }
}
