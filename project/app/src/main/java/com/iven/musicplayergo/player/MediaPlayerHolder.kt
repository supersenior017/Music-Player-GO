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
import android.os.Parcelable
import android.os.PowerManager
import android.view.KeyEvent
import com.iven.musicplayergo.MainActivity
import com.iven.musicplayergo.R
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.music.Music
import com.iven.musicplayergo.ui.Utils
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
    private var sPlayOnFocusGain = false

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
    private lateinit var mediaPlayer: MediaPlayer
    private var mExecutor: ScheduledExecutorService? = null
    private var mSeekBarPositionUpdateTask: Runnable? = null

    //first: current song, second: isFromQueue
    lateinit var currentSong: Pair<Music, Boolean>
    private lateinit var mPlayingAlbumSongs: List<Music>

    var currentVolumeInPercent = goPreferences.latestVolume
    val playerPosition get() = if (!isMediaPlayer) goPreferences.latestPlayedSong?.second!! else mediaPlayer.currentPosition

    //media player state/booleans
    val isPlaying get() = isMediaPlayer && mediaPlayer.isPlaying
    val isMediaPlayer get() = ::mediaPlayer.isInitialized

    private var sNotificationForeground = false
    private var sCreateNotificationOnResume = false
    private var sUpdateNotification = false

    val isCurrentSong get() = ::currentSong.isInitialized
    var isRepeat = false

    var isQueue = false
    var isQueueStarted = false
    private lateinit var preQueueSong: Pair<Music, List<Music>>
    var queueSongs = mutableListOf<Music>()

    var isSongRestoredFromPrefs = false
    var isSongFromLovedSongs = Pair(false, 0)

    var state = PAUSED
    var isPlay = false

    //receivers
    private lateinit var mMediaButtonsReceiver: MediaButtonIntentReceiver

    //notifications
    private lateinit var mNotificationActionsReceiver: NotificationReceiver
    private lateinit var mMusicNotificationManager: MusicNotificationManager

    private fun startForeground() {
        if (!sNotificationForeground || sCreateNotificationOnResume) {
            sNotificationForeground = true
            if (sCreateNotificationOnResume) sCreateNotificationOnResume = false
            playerService.startForeground(
                NOTIFICATION_ID,
                mMusicNotificationManager.createNotification()
            )
        } else {
            if (sUpdateNotification) {
                sUpdateNotification = false
                updateNotification()
            }
        }
    }

    fun updateNotification() {
        mMusicNotificationManager.notificationManager
            .notify(NOTIFICATION_ID, mMusicNotificationManager.createNotification())
    }

    fun registerActionsReceiver() {
        mNotificationActionsReceiver = NotificationReceiver()
        val intentFilter = IntentFilter().apply {
            addAction(REPEAT_ACTION)
            addAction(PREV_ACTION)
            addAction(PLAY_PAUSE_ACTION)
            addAction(NEXT_ACTION)
            addAction(CLOSE_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        playerService.registerReceiver(mNotificationActionsReceiver, intentFilter)
    }

    private fun unregisterActionsReceiver() {
        try {
            playerService.unregisterReceiver(mNotificationActionsReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    //https://stackoverflow.com/a/44709784
    private fun registerMediaButtonsReceiver() {
        mMediaButtonsReceiver = MediaButtonIntentReceiver()
        val mediaFilter = IntentFilter(Intent.ACTION_MEDIA_BUTTON).apply {
            priority = 10000
        }
        playerService.registerReceiver(mMediaButtonsReceiver, mediaFilter)
    }

    private fun unregisterMediaButtonsReceiver() {
        if (::mMediaButtonsReceiver.isInitialized) {
            try {
                playerService.unregisterReceiver(mMediaButtonsReceiver)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    fun setCurrentSong(song: Music, songs: List<Music>, isFromQueue: Boolean) {
        currentSong = Pair(song, isFromQueue)
        mPlayingAlbumSongs = songs
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {

        mediaPlayerInterface.onStateChanged()
        mediaPlayerInterface.onPlaybackCompleted()

        when {
            isRepeat -> if (isMediaPlayer) resetSong()
            isQueue -> manageQueue(true)
            else -> skip(true)
        }
    }

    fun onResumeActivity() {
        if (mExecutor == null) startUpdatingCallbackWithPosition()
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

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                mAudioFocusRequestOreo =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                        setAudioAttributes(AudioAttributes.Builder().run {
                            setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        })
                        setOnAudioFocusChangeListener(mOnAudioFocusChangeListener, mHandler)
                        build()
                    }
                mAudioManager.requestAudioFocus(mAudioFocusRequestOreo)
            }
            else -> mAudioManager.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun giveUpAudioFocus() {

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> mAudioManager.abandonAudioFocusRequest(
                mAudioFocusRequestOreo
            )
            else -> {
                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener)
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
            }
        }
    }

    private fun setStatus(status: Int) {
        state = status
        mediaPlayerInterface.onStateChanged()
    }

    private fun resumeMediaPlayer() {
        if (!isPlaying) {
            if (isMediaPlayer) mediaPlayer.start()
            setStatus(if (isSongRestoredFromPrefs) PLAYING else RESUMED)

            if (!sNotificationForeground) sCreateNotificationOnResume = true

            startForeground()

            isSongRestoredFromPrefs = false
            if (!isPlay) isPlay = true
        }
    }

    private fun pauseMediaPlayer() {
        setStatus(PAUSED)
        mediaPlayer.pause()
        playerService.stopForeground(false)
        updateNotification()
    }

    private fun resetSong() {
        isRepeat = false
        mediaPlayer.seekTo(0)
        mediaPlayer.start()
        setStatus(PLAYING)
    }

    private fun manageQueue(isNext: Boolean) {

        if (isSongRestoredFromPrefs) isSongRestoredFromPrefs = false

        when {
            isQueueStarted -> currentSong = Pair(getSkipSong(isNext), true)
            else -> {
                setCurrentSong(queueSongs[0], queueSongs, true)
                isQueueStarted = true
            }
        }
        initMediaPlayer(currentSong.first)
    }

    fun restorePreQueueSongs() {
        setCurrentSong(preQueueSong.first, preQueueSong.second, false)
    }

    private fun getSkipSong(isNext: Boolean): Music {
        val currentIndex = mPlayingAlbumSongs.indexOf(currentSong.first)
        try {
            return mPlayingAlbumSongs[if (isNext) currentIndex + 1 else currentIndex - 1]
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
            return when {
                isQueue -> {

                    when {

                        isNext -> {
                            setQueueEnabled(false)
                            restorePreQueueSongs()
                            getSkipSong(true)
                        }
                        else -> {
                            isQueueStarted = false
                            preQueueSong.first
                        }
                    }
                }
                else -> if (currentIndex != 0) mPlayingAlbumSongs[0] else mPlayingAlbumSongs[mPlayingAlbumSongs.size - 1]
            }
        }
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private fun startUpdatingCallbackWithPosition() {

        if (mSeekBarPositionUpdateTask == null) mSeekBarPositionUpdateTask =
            Runnable { updateProgressCallbackTask() }

        mExecutor = Executors.newSingleThreadScheduledExecutor()
        mExecutor?.scheduleAtFixedRate(
            mSeekBarPositionUpdateTask!!,
            0,
            1000,
            TimeUnit.MILLISECONDS
        )
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private fun stopUpdatingCallbackWithPosition() {
        mExecutor?.shutdownNow()
        mExecutor = null
        mSeekBarPositionUpdateTask = null
    }

    private fun updateProgressCallbackTask() {
        if (isPlaying) {
            val currentPosition = mediaPlayer.currentPosition
            mediaPlayerInterface.onPositionChanged(currentPosition)
        }
    }

    fun instantReset() {
        if (isMediaPlayer) {
            if (mediaPlayer.currentPosition < 5000) skip(false) else resetSong()
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
                mediaPlayer.reset()
            } else {
                mediaPlayer = MediaPlayer().apply {

                    EqualizerUtils.openAudioEffectSession(
                        playerService.applicationContext,
                        audioSessionId
                    )

                    setOnPreparedListener(this@MediaPlayerHolder)
                    setOnCompletionListener(this@MediaPlayerHolder)
                    setWakeMode(playerService, PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                }

                mMusicNotificationManager = playerService.musicNotificationManager
                tryToGetAudioFocus()
                registerMediaButtonsReceiver()

                setPreciseVolume(currentVolumeInPercent)
            }

            mediaPlayer.setDataSource(song.path)
            mediaPlayer.prepare()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        if (mExecutor == null) startUpdatingCallbackWithPosition()

        if (isRepeat) isRepeat = false

        if (isSongRestoredFromPrefs) {
            setPreciseVolume(currentVolumeInPercent)
            mediaPlayer.seekTo(goPreferences.latestPlayedSong?.second!!)
        } else if (isSongFromLovedSongs.first) {
            mediaPlayer.seekTo(isSongFromLovedSongs.second)
            isSongFromLovedSongs = Pair(false, 0)
        }

        if (isQueue) mediaPlayerInterface.onQueueStartedOrEnded(isQueueStarted)

        if (isPlay) {
            sUpdateNotification = true
            setStatus(PLAYING)
            mediaPlayer.start()
            startForeground()
        }
    }

    fun openEqualizer(activity: Activity) {
        EqualizerUtils.openEqualizer(activity, mediaPlayer)
    }

    fun release() {
        if (isMediaPlayer) {
            EqualizerUtils.closeAudioEffectSession(
                playerService,
                mediaPlayer.audioSessionId
            )
            mediaPlayer.release()
            giveUpAudioFocus()
            stopUpdatingCallbackWithPosition()
            unregisterMediaButtonsReceiver()
        }
        unregisterActionsReceiver()
    }

    fun resumeOrPause() {
        if (isPlaying) pauseMediaPlayer() else resumeMediaPlayer()
    }

    fun reset() {
        isRepeat = !isRepeat
        Utils.makeToast(
            playerService,
            playerService.getString(if (isRepeat) R.string.repeat_enabled else R.string.repeat_disabled)
        )
    }

    fun setQueueEnabled(enabled: Boolean) {
        isQueue = enabled
        isQueueStarted = false

        when {
            isQueue -> {
                preQueueSong = Pair(currentSong.first, mPlayingAlbumSongs)
                isQueue = true
                mediaPlayerInterface.onQueueEnabled()
            }
            else -> {
                queueSongs.clear()
                mediaPlayerInterface.onQueueCleared()
                mediaPlayerInterface.onQueueStartedOrEnded(false)
            }
        }
    }

    fun skip(isNext: Boolean) {
        when {
            isQueue -> manageQueue(isNext)
            else -> {
                currentSong = Pair(getSkipSong(isNext), false)
                initMediaPlayer(currentSong.first)
            }
        }
    }

    fun seekTo(position: Int) {
        if (isMediaPlayer) mediaPlayer.seekTo(position)
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
                        AUDIO_NO_FOCUS_CAN_DUCK -> mediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK)
                        else -> mediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL)
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

    /* Sets the volume of the media player */
    fun setPreciseVolume(percent: Int) {

        currentVolumeInPercent = percent

        if (isMediaPlayer) {
            fun volFromPercent(percent: Int): Float {
                if (percent == 100) return 1f
                return (1 - (ln((101 - percent).toFloat()) / ln(101f)))
            }

            val new = volFromPercent(percent)
            mediaPlayer.setVolume(new, new)
        }
    }

    fun stopPlaybackService(stopPlayback: Boolean) {
        if (playerService.isRunning && isMediaPlayer && stopPlayback) {
            playerService.stopForeground(true)
            playerService.stopSelf()
        }
        mediaPlayerInterface.onClose()
    }

    private inner class NotificationReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action != null) {
                when (action) {
                    PREV_ACTION -> instantReset()
                    PLAY_PAUSE_ACTION -> resumeOrPause()
                    NEXT_ACTION -> skip(true)
                    REPEAT_ACTION -> {
                        reset()
                        mediaPlayerInterface.onUpdateRepeatStatus()
                    }
                    CLOSE_ACTION -> if (playerService.isRunning && isMediaPlayer) stopPlaybackService(
                        true
                    )

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (::currentSong.isInitialized && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (::currentSong.isInitialized && goPreferences.isHeadsetPlugEnabled) resumeMediaPlayer()

                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED ->
                        when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> if (isCurrentSong && goPreferences.isHeadsetPlugEnabled) resumeMediaPlayer()
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> if (isCurrentSong && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                        }

                    Intent.ACTION_HEADSET_PLUG -> if (isCurrentSong && goPreferences.isHeadsetPlugEnabled) {
                        when (intent.getIntExtra("state", -1)) {
                            //0 means disconnected
                            HEADSET_DISCONNECTED -> if (isCurrentSong && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                            //1 means connected
                            HEADSET_CONNECTED -> if (isCurrentSong && goPreferences.isHeadsetPlugEnabled) resumeMediaPlayer()
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> if (isPlaying && goPreferences.isHeadsetPlugEnabled) pauseMediaPlayer()
                }
            }
            if (isOrderedBroadcast) abortBroadcast()
        }
    }

    private inner class MediaButtonIntentReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val intentAction = intent.action
            if (Intent.ACTION_MEDIA_BUTTON != intentAction) {
                return
            }
            val event: KeyEvent =
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_KEY_EVENT) as KeyEvent
            val action: Int = event.action
            if (action == KeyEvent.ACTION_DOWN) { // do something
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> resumeOrPause()
                    KeyEvent.KEYCODE_MEDIA_CLOSE -> stopPlaybackService(true)
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> skip(false)
                    KeyEvent.KEYCODE_MEDIA_NEXT -> skip(true)
                    KeyEvent.KEYCODE_MEDIA_STOP -> stopPlaybackService(true)
                    KeyEvent.KEYCODE_MEDIA_REWIND -> resetSong()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> pauseMediaPlayer()
                    KeyEvent.KEYCODE_MEDIA_PLAY -> resumeMediaPlayer()
                }
            }
            if (isOrderedBroadcast) abortBroadcast()
        }
    }
}
