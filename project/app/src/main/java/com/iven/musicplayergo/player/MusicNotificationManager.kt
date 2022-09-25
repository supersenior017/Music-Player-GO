package com.iven.musicplayergo.player


import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.parseAsHtml
import androidx.media.app.NotificationCompat.MediaStyle
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.extensions.toFilenameWithoutExtension
import com.iven.musicplayergo.extensions.waitForCover
import com.iven.musicplayergo.models.NotificationAction
import com.iven.musicplayergo.ui.MainActivity
import com.iven.musicplayergo.utils.Theming
import com.iven.musicplayergo.utils.Versioning


class MusicNotificationManager(private val playerService: PlayerService) {

    //notification manager/builder
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private val mNotificationManagerCompat get() = NotificationManagerCompat.from(playerService)
    private var mNotificationColor = Color.BLACK

    private val mNotificationActions
        @SuppressLint("RestrictedApi")
        get() = mNotificationBuilder.mActions

    private fun getPendingIntent(playerAction: String): PendingIntent {
        val intent = Intent().apply {
            action = playerAction
            component = ComponentName(playerService, PlayerService::class.java)
        }
        val flags = if (Versioning.isMarshmallow()) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(playerService, GoConstants.NOTIFICATION_INTENT_REQUEST_CODE, intent, flags)
    }

    private val notificationActions: NotificationAction get() = GoPreferences.getPrefsInstance().notificationActions

    fun createNotification(onCreated: (Notification) -> Unit) {

        mNotificationBuilder =
            NotificationCompat.Builder(playerService, GoConstants.NOTIFICATION_CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val openPlayerIntent = Intent(playerService, MainActivity::class.java)
        openPlayerIntent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val flags = if (Versioning.isMarshmallow()) {
            PendingIntent.FLAG_IMMUTABLE or 0
        } else {
            0
        }
        val contentIntent = PendingIntent.getActivity(
            playerService, GoConstants.NOTIFICATION_INTENT_REQUEST_CODE,
            openPlayerIntent, flags
        )

        mNotificationBuilder
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setShowWhen(false)
            .setOngoing(playerService.mediaPlayerHolder.isPlaying)
            .addAction(getNotificationAction(notificationActions.first))
            .addAction(getNotificationAction(GoConstants.PREV_ACTION))
            .addAction(getNotificationAction(GoConstants.PLAY_PAUSE_ACTION))
            .addAction(getNotificationAction(GoConstants.NEXT_ACTION))
            .addAction(getNotificationAction(notificationActions.second))
            .setStyle(
                MediaStyle()
                    .setMediaSession(playerService.getMediaSession()?.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .priority = NotificationCompat.PRIORITY_DEFAULT

        updateNotificationContent {
            onCreated(mNotificationBuilder.build())
        }
    }

    fun updateNotification() {
        if (::mNotificationBuilder.isInitialized) {
            mNotificationBuilder.setOngoing(playerService.mediaPlayerHolder.isPlaying)
            with(mNotificationManagerCompat) {
                notify(GoConstants.NOTIFICATION_ID, mNotificationBuilder.build())
            }
        }
    }

    fun cancelNotification() {
        with(mNotificationManagerCompat) {
            cancel(GoConstants.NOTIFICATION_ID)
        }
    }

    fun onHandleNotificationUpdate(isAdditionalActionsChanged: Boolean) {
        if (::mNotificationBuilder.isInitialized) {
            if (!isAdditionalActionsChanged) {
                updateNotificationContent()
                updateNotificationContent {
                    updateNotification()
                }
            } else {
                mNotificationActions[0] =
                    getNotificationAction(notificationActions.first)
                mNotificationActions[4] =
                    getNotificationAction(notificationActions.second)
                updateNotification()
            }
        }
    }

    fun onSetNotificationColor(color: Int) {
        mNotificationColor = color
        onHandleNotificationUpdate(isAdditionalActionsChanged = false)
    }

    fun updateNotificationContent(onDone: (() -> Unit)? = null) {
        val mediaPlayerHolder = playerService.mediaPlayerHolder

        (mediaPlayerHolder.currentSongFM ?: mediaPlayerHolder.currentSong)?.let { song ->
            mNotificationBuilder
                .setContentText(song.artist)
                .setContentTitle(
                    playerService.getString(
                        R.string.song_title_notification,
                        if (GoPreferences.getPrefsInstance().songsVisualization == GoConstants.FN) {
                            song.displayName.toFilenameWithoutExtension()
                        } else {
                            song.title
                        }
                    ).parseAsHtml()
                )
                .setSubText(song.album)
                .setColor(mNotificationColor)
                .setColorized(true)
                .setSmallIcon(getNotificationSmallIcon(mediaPlayerHolder))

            song.albumId?.waitForCover(playerService) { bitmap, _ ->
                mNotificationBuilder.setLargeIcon(bitmap)
                onDone?.invoke()
            }
        }
    }

    private fun getNotificationSmallIcon(mediaPlayerHolder: MediaPlayerHolder) = if (mediaPlayerHolder.isQueue != null && mediaPlayerHolder.isQueueStarted) {
        R.drawable.ic_music_note
    } else {
        when (mediaPlayerHolder.launchedBy) {
            GoConstants.FOLDER_VIEW -> R.drawable.ic_folder_music
            GoConstants.ALBUM_VIEW -> R.drawable.ic_library_music
            else -> R.drawable.ic_music_note
        }
    }

    fun updatePlayPauseAction() {
        if (::mNotificationBuilder.isInitialized) {
            mNotificationActions[2] =
                getNotificationAction(GoConstants.PLAY_PAUSE_ACTION)
        }
    }

    fun updateRepeatIcon() {
        if (::mNotificationBuilder.isInitialized) {
            mNotificationActions[0] =
                getNotificationAction(GoConstants.REPEAT_ACTION)
            updateNotification()
        }
    }

    fun updateFavoriteIcon() {
        if (::mNotificationBuilder.isInitialized) {
            mNotificationActions[0] =
                getNotificationAction(GoConstants.FAVORITE_ACTION)
            updateNotification()
        }
    }

    private fun getNotificationAction(action: String): NotificationCompat.Action {
        val icon = Theming.getNotificationActionIcon(action, playerService.mediaPlayerHolder, isNotification = true)
        return NotificationCompat.Action.Builder(icon, action, getPendingIntent(action)).build()
    }

    @TargetApi(26)
    private fun createNotificationChannel() {
        val name = playerService.getString(R.string.app_name)
        val channel = NotificationChannel(GoConstants.NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = name
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            playerService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
