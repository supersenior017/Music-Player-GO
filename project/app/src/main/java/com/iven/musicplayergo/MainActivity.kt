package com.iven.musicplayergo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.callbacks.onShow
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.google.android.material.tabs.TabLayout
import com.iven.musicplayergo.adapters.QueueAdapter
import com.iven.musicplayergo.fragments.AllMusicFragment
import com.iven.musicplayergo.fragments.ArtistsFoldersFragment
import com.iven.musicplayergo.fragments.ArtistsFoldersFragment.Companion.TAG_ARTISTS
import com.iven.musicplayergo.fragments.ArtistsFoldersFragment.Companion.TAG_FOLDERS
import com.iven.musicplayergo.fragments.DetailsFragment
import com.iven.musicplayergo.fragments.SettingsFragment
import com.iven.musicplayergo.music.Music
import com.iven.musicplayergo.music.MusicLoader
import com.iven.musicplayergo.music.MusicUtils
import com.iven.musicplayergo.player.*
import com.iven.musicplayergo.ui.ThemeHelper
import com.iven.musicplayergo.ui.UIControlInterface
import com.iven.musicplayergo.ui.Utils
import de.halfbit.edgetoedge.Edge
import de.halfbit.edgetoedge.edgeToEdge
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.player_controls_panel.*
import kotlin.properties.Delegates

private const val MUSIC_LOADER_ID = 25
const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 2588
const val RESTORE_SETTINGS_FRAGMENT = "restore_settings_fragment_key"

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity(R.layout.main_activity), UIControlInterface,
    LoaderManager.LoaderCallbacks<Boolean> {

    //colors
    private var mResolvedAccentColor: Int by Delegates.notNull()
    private var mResolvedAlphaAccentColor: Int by Delegates.notNull()
    private var mResolvedIconsColor: Int by Delegates.notNull()
    private var mResolvedDisabledIconsColor: Int by Delegates.notNull()

    //fragments
    private var mArtistsFragment: ArtistsFoldersFragment? = null
    private var mAllMusicFragment: AllMusicFragment? = null
    private var mFoldersFragment: ArtistsFoldersFragment? = null
    private var mSettingsFragment: SettingsFragment? = null

    private lateinit var mDetailsFragment: DetailsFragment
    private val sDetailsFragmentExpanded get() = ::mDetailsFragment.isInitialized && mDetailsFragment.isAdded
    private var sRevealAnimationRunning = false

    private var mActiveFragments: MutableList<String>? = null

    private var sThemeChanged = false
    private var sRestoreSettingsFragment = false

    //views
    private lateinit var mLoadingProgress: ProgressBar
    private lateinit var mViewPager: ViewPager
    private lateinit var mTabsLayout: TabLayout
    private lateinit var mPlayerControlsContainer: View

    private var sLandscape = false

    //settings/controls panel
    private lateinit var mPlayingArtist: TextView
    private lateinit var mPlayingSong: TextView
    private lateinit var mSeekProgressBar: ProgressBar
    private lateinit var mPlayPauseButton: ImageButton
    private lateinit var mLovedSongsButton: ImageButton
    private lateinit var mLoveSongsNumber: TextView
    private lateinit var mQueueButton: ImageButton

    private var mControlsPaddingNoTabs: Int by Delegates.notNull()
    private var mControlsPaddingNormal: Int by Delegates.notNull()
    private var mControlsPaddingEnd: Int by Delegates.notNull()
    private val sTabsEnabled = goPreferences.isTabsEnabled

    //now playing
    private lateinit var mNowPlayingDialog: MaterialDialog
    private lateinit var mSeekBarNP: SeekBar
    private lateinit var mSongTextNP: TextView
    private lateinit var mArtistAlbumTextNP: TextView
    private lateinit var mSongSeekTextNP: TextView
    private lateinit var mSongDurationTextNP: TextView
    private lateinit var mSkipPrevButtonNP: ImageButton
    private lateinit var mPlayPauseButtonNP: ImageButton
    private lateinit var mSkipNextButtonNP: ImageButton
    private lateinit var mRepeatNP: ImageButton
    private lateinit var mVolumeSeekBarNP: SeekBar
    private lateinit var mLoveButtonNP: ImageButton
    private lateinit var mVolumeNP: ImageButton
    private lateinit var mRatesTextNP: TextView


    //music player things
    private var mMusicLoader: Loader<Boolean>? = null

    //booleans
    private var sUserIsSeeking = false

    private lateinit var mQueueDialog: Pair<MaterialDialog, QueueAdapter>

    //the player
    private lateinit var mMediaPlayerHolder: MediaPlayerHolder
    private val isMediaPlayerHolder get() = ::mMediaPlayerHolder.isInitialized
    private val isNowPlaying get() = ::mNowPlayingDialog.isInitialized && mNowPlayingDialog.isShowing

    //our PlayerService shit
    private lateinit var mPlayerService: PlayerService
    private var sBound = false
    private lateinit var mBindingIntent: Intent

    private fun checkIsPlayer(showError: Boolean): Boolean {
        return mMediaPlayerHolder.apply {
            if (!isMediaPlayer && !mMediaPlayerHolder.isSongRestoredFromPrefs && showError) EqualizerUtils.notifyNoSessionId(
                this@MainActivity
            )
        }.isMediaPlayer
    }

    //Defines callbacks for service binding, passed to bindService()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            val binder = service as PlayerService.LocalBinder
            mPlayerService = binder.getService()
            sBound = true
            mMediaPlayerHolder = mPlayerService.mediaPlayerHolder
            mMediaPlayerHolder.mediaPlayerInterface = mMediaPlayerInterface

            launchMusicLoading()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            sBound = false
        }
    }

    private fun doBindService() {

        if (mLoadingProgress.visibility != View.VISIBLE) mLoadingProgress.visibility = View.VISIBLE

        // Bind to LocalService
        mBindingIntent = Intent(this, PlayerService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onBackPressed() {
        if (sDetailsFragmentExpanded) {
            closeDetailsFragment(null)
        } else {
            if (mViewPager.currentItem != 0) mViewPager.currentItem = 0 else
                if (isMediaPlayerHolder && mMediaPlayerHolder.isPlaying) Utils.stopPlaybackDialog(
                    this,
                    mMediaPlayerHolder
                ) else super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (sThemeChanged) {
            super.onSaveInstanceState(outState)
            outState.putBoolean(
                RESTORE_SETTINGS_FRAGMENT,
                true
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (sBound) unbindService(connection)
        if (isMediaPlayerHolder && !mMediaPlayerHolder.isPlaying && ::mPlayerService.isInitialized && mPlayerService.isRunning) {
            mPlayerService.stopForeground(true)
            stopService(mBindingIntent)
        }
    }

    //restore recycler views state
    override fun onResume() {
        super.onResume()
        if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) mMediaPlayerHolder.onResumeActivity()
    }

    //save recycler views state
    override fun onPause() {
        super.onPause()
        if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) mMediaPlayerHolder.onPauseActivity()
    }

    //manage request permission result, continue loading ui if permissions was granted
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_EXTERNAL_STORAGE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                // permission was granted, yay! Do the
                // music-related task you need to do.
                    doBindService()
                else
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                    Utils.dismissOnPermissionDenied(this)
            }
        }
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        //set ui theme
        setTheme(ThemeHelper.getAccentedTheme().first)
        return super.onCreateView(parent, name, context, attrs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //init views
        getViewsAndResources()

        if (goPreferences.isEdgeToEdge) {
            window?.apply {
                if (!Utils.isAndroidQ()) {
                    statusBarColor = Color.TRANSPARENT
                    navigationBarColor = Color.TRANSPARENT
                }
                ThemeHelper.handleLightSystemBars(decorView)
            }
            edgeToEdge {
                mainView.fit { Edge.Top + Edge.Bottom }
            }
        }

        setupControlsPanelSpecs()

        initMediaButtons()

        mActiveFragments = goPreferences.activeFragments?.toMutableList()

        sLandscape = ThemeHelper.isDeviceLand(resources)

        sRestoreSettingsFragment =
            savedInstanceState?.getBoolean(RESTORE_SETTINGS_FRAGMENT) ?: intent.getBooleanExtra(
                RESTORE_SETTINGS_FRAGMENT,
                false
            )

        if (Utils.hasToAskForReadStoragePermission(this)) Utils.manageAskForReadStoragePermission(
            this
        ) else doBindService()
    }

    private fun getViewsAndResources() {

        mLoadingProgress = loading_progress_bar
        mViewPager = pager
        mTabsLayout = tab_layout
        mPlayerControlsContainer = playing_songs_container

        //controls panel
        mPlayingSong = playing_song
        mPlayingArtist = playing_artist
        mSeekProgressBar = song_progress
        mPlayPauseButton = play_pause_button

        mLovedSongsButton = loved_songs_button
        mLoveSongsNumber = loved_songs_number
        mQueueButton = queue_button

        mResolvedAccentColor = ThemeHelper.resolveThemeAccent(this)
        mResolvedAlphaAccentColor =
            ThemeHelper.getAlphaAccent(this, ThemeHelper.getAlphaForAccent())
        mResolvedIconsColor =
            ThemeHelper.resolveColorAttr(this, android.R.attr.textColorPrimary)
        mResolvedDisabledIconsColor =
            ThemeHelper.resolveColorAttr(this, android.R.attr.colorButtonNormal)

        resources.apply {
            mControlsPaddingNormal =
                getDimensionPixelSize(R.dimen.player_controls_padding_normal)
            mControlsPaddingEnd = getDimensionPixelSize(R.dimen.player_controls_padding_end)
            mControlsPaddingNoTabs =
                getDimensionPixelSize(R.dimen.player_controls_padding_no_tabs)
        }
    }

    override fun onLoaderReset(loader: Loader<Boolean>) {
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Boolean> {
        return MusicLoader(this)
    }

    override fun onLoadFinished(loader: Loader<Boolean>, hasLoaded: Boolean) {
        LoaderManager.getInstance(this).destroyLoader(MUSIC_LOADER_ID)
        if (hasLoaded && !musicLibrary.allSongsFiltered.isNullOrEmpty()) finishSetup() else Utils.notifyLoadingError(
            this
        )
    }

    private fun launchMusicLoading() {
        if (mMusicLoader == null) LoaderManager.getInstance(this).initLoader(
            MUSIC_LOADER_ID,
            null,
            this
        )
    }

    private fun finishSetup() {

        if (mLoadingProgress.visibility != View.GONE) mLoadingProgress.visibility = View.GONE

        initViewPager()

        //handle restoring: handle external app intent or restore playback
        if (intent != null && Intent.ACTION_VIEW == intent.action && intent.data != null)
            handleIntent(intent)
        else
            restorePlayerStatus()
    }

    private fun initViewPager() {

        initActiveFragmentsOrTabs(true)

        val pagerAdapter = ScreenSlidePagerAdapter(supportFragmentManager)
        mViewPager.offscreenPageLimit = mActiveFragments?.size?.minus(1)!!
        mViewPager.adapter = pagerAdapter

        if (sTabsEnabled) {
            mTabsLayout.apply {

                tabIconTint = ColorStateList.valueOf(mResolvedAlphaAccentColor)

                setupWithViewPager(mViewPager)

                initActiveFragmentsOrTabs(false)

                if (!sRestoreSettingsFragment) getTabAt(0)?.icon?.setTint(
                    mResolvedAccentColor
                )

                addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

                    override fun onTabSelected(tab: TabLayout.Tab) {
                        if (sDetailsFragmentExpanded) closeDetailsFragment(tab) else
                            tab.icon?.setTint(mResolvedAccentColor)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab) {
                        tab.icon?.setTint(mResolvedAlphaAccentColor)
                    }

                    override fun onTabReselected(tab: TabLayout.Tab) {
                        if (sDetailsFragmentExpanded) closeDetailsFragment(null)
                    }
                })
            }
        }

        if (sRestoreSettingsFragment) mViewPager.currentItem =
            mViewPager.offscreenPageLimit
    }

    private fun setupControlsPanelSpecs() {
        if (!sTabsEnabled) mTabsLayout.visibility = View.GONE
        mPlayerControlsContainer.setPadding(
            mControlsPaddingNoTabs,
            if (sTabsEnabled) mControlsPaddingNormal else mControlsPaddingNoTabs,
            mControlsPaddingEnd,
            if (sTabsEnabled) mControlsPaddingNormal else mControlsPaddingNoTabs
        )
    }

    private fun initActiveFragmentsOrTabs(onInitActiveFragments: Boolean) {
        mActiveFragments?.iterator()?.forEach {
            if (onInitActiveFragments) initFragmentAtIndex(it.toInt()) else
                mTabsLayout.getTabAt(mActiveFragments?.indexOf(it)!!)?.setIcon(
                    ThemeHelper.getTabIcon(
                        it.toInt()
                    )
                )
        }
    }

    private fun initFragmentAtIndex(index: Int) {
        when (index) {
            0 -> if (mArtistsFragment == null) mArtistsFragment =
                ArtistsFoldersFragment.newInstance(TAG_ARTISTS)
            1 -> if (mAllMusicFragment == null) mAllMusicFragment = AllMusicFragment.newInstance()
            2 -> if (mFoldersFragment == null) mFoldersFragment =
                ArtistsFoldersFragment.newInstance(TAG_FOLDERS)
            else -> if (mSettingsFragment == null) mSettingsFragment =
                SettingsFragment.newInstance()
        }
    }

    private fun handleOnNavigationItemSelected(itemId: Int): Fragment? {
        return when (itemId) {
            0 -> getFragmentForIndex(mActiveFragments?.get(0)?.toInt())
            1 -> getFragmentForIndex(mActiveFragments?.get(1)?.toInt())
            2 -> getFragmentForIndex(mActiveFragments?.get(2)?.toInt())
            else -> getFragmentForIndex(mActiveFragments?.get(3)?.toInt())
        }
    }

    private fun getFragmentForIndex(index: Int?): Fragment? {
        return when (index) {
            0 -> mArtistsFragment
            1 -> mAllMusicFragment
            2 -> mFoldersFragment
            else -> mSettingsFragment
        }
    }

    private fun openDetailsFragment(
        selectedArtistOrFolder: String?,
        isFolder: Boolean
    ) {

        mDetailsFragment =
            DetailsFragment.newInstance(
                selectedArtistOrFolder,
                isFolder,
                MusicUtils.getPlayingAlbumPosition(selectedArtistOrFolder, mMediaPlayerHolder)
            )
        supportFragmentManager.beginTransaction()
            .addToBackStack(null)
            .add(
                R.id.container,
                mDetailsFragment
            )
            .commit()
    }

    private fun closeDetailsFragment(tab: TabLayout.Tab?) {
        if (!sRevealAnimationRunning) {
            mDetailsFragment.onHandleBackPressed(this).apply {
                sRevealAnimationRunning = true
                tab?.icon?.setTint(mResolvedAccentColor)
                doOnEnd {
                    super.onBackPressed()
                    sRevealAnimationRunning = false
                }
            }
        }
    }

    private fun initMediaButtons() {

        mPlayPauseButton.setOnClickListener { resumeOrPause() }

        mQueueButton.setOnLongClickListener {
            if (checkIsPlayer(true) && mMediaPlayerHolder.isQueue) Utils.showClearQueueDialog(
                this,
                mMediaPlayerHolder
            )
            return@setOnLongClickListener true
        }

        mLovedSongsButton.setOnLongClickListener {
            if (!goPreferences.lovedSongs.isNullOrEmpty()) Utils.showClearLovedSongDialog(
                this,
                this
            )
            return@setOnLongClickListener true
        }

        onLovedSongsUpdate(false)

        mPlayerControlsContainer.setOnLongClickListener { playerControlsContainer ->
            if (checkIsPlayer(true)) openPlayingArtistAlbum(playerControlsContainer)
            return@setOnLongClickListener true
        }
    }

    private fun setSeekBarProgressListener() {

        mSeekBarNP.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                val defaultPositionColor = mSongSeekTextNP.currentTextColor
                var userSelectedPosition = 0

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (sUserIsSeeking) {
                        userSelectedPosition = progress
                        mSongSeekTextNP.setTextColor(
                            mResolvedAccentColor
                        )
                    }
                    mSongSeekTextNP.text =
                        MusicUtils.formatSongDuration(progress.toLong(), false)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    sUserIsSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (sUserIsSeeking) {
                        mSongSeekTextNP.setTextColor(defaultPositionColor)
                    }
                    sUserIsSeeking = false
                    if (mMediaPlayerHolder.state != PLAYING) {
                        mSeekProgressBar.progress = userSelectedPosition
                        mSeekBarNP.progress = userSelectedPosition
                    }
                    mMediaPlayerHolder.seekTo(userSelectedPosition)
                }
            })
    }

    private fun setupPreciseVolumeHandler() {

        var isUserSeeking = false

        mMediaPlayerHolder.currentVolumeInPercent.apply {
            mVolumeNP.setImageResource(ThemeHelper.getPreciseVolumeIcon(this))
            mVolumeSeekBarNP.progress = this
        }

        mVolumeSeekBarNP.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                if (isUserSeeking) {

                    mMediaPlayerHolder.setPreciseVolume(progress)

                    mVolumeNP.setImageResource(ThemeHelper.getPreciseVolumeIcon(progress))

                    ThemeHelper.updateIconTint(
                        mVolumeNP,
                        mResolvedAccentColor
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                ThemeHelper.updateIconTint(
                    mVolumeNP,
                    mResolvedIconsColor
                )
            }
        })
    }

    fun openNowPlaying(view: View) {

        if (checkIsPlayer(true) && mMediaPlayerHolder.isCurrentSong) {

            mNowPlayingDialog = MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {

                cornerRadius(res = R.dimen.md_corner_radius)

                customView(R.layout.now_playing)

                val customView = getCustomView()

                mSongTextNP = customView.findViewById(R.id.np_song)
                mSongTextNP.isSelected = true
                mArtistAlbumTextNP = customView.findViewById(R.id.np_artist_album)
                mArtistAlbumTextNP.isSelected = true
                mSeekBarNP = customView.findViewById(R.id.np_seek_bar)

                mSongSeekTextNP = customView.findViewById(R.id.np_seek)
                mSongDurationTextNP = customView.findViewById(R.id.np_duration)

                mSkipPrevButtonNP = customView.findViewById(R.id.np_skip_prev)
                mSkipPrevButtonNP.setOnClickListener { skip(false) }

                mPlayPauseButtonNP = customView.findViewById(R.id.np_play)
                mPlayPauseButtonNP.setOnClickListener { resumeOrPause() }

                mSkipNextButtonNP = customView.findViewById(R.id.np_skip_next)
                mSkipNextButtonNP.setOnClickListener { skip(true) }

                mRepeatNP = customView.findViewById(R.id.np_repeat)

                ThemeHelper.updateIconTint(
                    mRepeatNP,
                    if (mMediaPlayerHolder.isRepeat) mResolvedAccentColor
                    else
                        mResolvedIconsColor
                )

                mRepeatNP.setOnClickListener { setRepeat() }

                mLoveButtonNP = customView.findViewById(R.id.np_love)
                mLoveButtonNP.setOnClickListener {
                    Utils.addToLovedSongs(
                        this@MainActivity,
                        mMediaPlayerHolder.currentSong.first,
                        mMediaPlayerHolder.playerPosition
                    )
                    onLovedSongsUpdate(false)
                }

                mVolumeSeekBarNP = customView.findViewById(R.id.np_volume_seek)
                mVolumeNP = customView.findViewById(R.id.np_volume)

                if (goPreferences.isPreciseVolumeEnabled) {
                    setupPreciseVolumeHandler()
                } else {
                    mVolumeSeekBarNP.isEnabled = false
                    ThemeHelper.updateIconTint(mVolumeNP, mResolvedDisabledIconsColor)
                }

                setSeekBarProgressListener()

                mRatesTextNP = customView.findViewById(R.id.np_rates)
                updateNowPlayingInfo()

                onShow {
                    mSeekBarNP.progress = mSeekProgressBar.progress
                }

                onDismiss {
                    mSeekBarNP.setOnSeekBarChangeListener(null)
                }

                if (goPreferences.isEdgeToEdge && !sLandscape) {
                    window?.apply {
                        ThemeHelper.handleLightSystemBars(decorView)
                        edgeToEdge {
                            customView.fit { Edge.Bottom }
                            decorView.fit { Edge.Top }
                        }
                    }
                }
            }
        }
    }

    override fun onThemeChanged(isAccent: Boolean) {
        sThemeChanged = !isAccent
        if (sThemeChanged) AppCompatDelegate.setDefaultNightMode(
            ThemeHelper.getDefaultNightMode(
                this
            )
        ) else if (mMediaPlayerHolder.isPlaying) mMediaPlayerHolder.updateNotification()
    }

    private fun updatePlayingStatus(isNowPlaying: Boolean) {
        val isPlaying = mMediaPlayerHolder.state != PAUSED
        val drawable =
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        if (isNowPlaying) mPlayPauseButtonNP.setImageResource(drawable) else mPlayPauseButton.setImageResource(
            drawable
        )
    }

    override fun onLovedSongsUpdate(clear: Boolean) {

        val lovedSongs = goPreferences.lovedSongs

        if (clear) {
            lovedSongs?.clear()
            goPreferences.lovedSongs = lovedSongs
        }

        val lovedSongsButtonColor = if (lovedSongs.isNullOrEmpty())
            mResolvedDisabledIconsColor else
            ContextCompat.getColor(this, R.color.red)
        ThemeHelper.updateIconTint(mLovedSongsButton, lovedSongsButtonColor)
        val songsNumber = if (lovedSongs.isNullOrEmpty()) 0 else lovedSongs.size
        mLoveSongsNumber.text = songsNumber.toString()
    }

    private fun restorePlayerStatus() {
        if (isMediaPlayerHolder) {
            //if we are playing and the activity was restarted
            //update the controls panel

            mMediaPlayerHolder.apply {

                if (isMediaPlayer) {

                    onResumeActivity()
                    updatePlayingInfo(true)

                } else {

                    isSongRestoredFromPrefs = goPreferences.latestPlayedSong != null

                    val song =
                        if (isSongRestoredFromPrefs) goPreferences.latestPlayedSong?.first else musicLibrary.randomMusic

                    val songs = MusicUtils.getAlbumSongs(song?.artist, song?.album)

                    isPlay = false

                    startPlayback(song, songs)

                    updatePlayingInfo(false)

                    mSeekProgressBar.progress =
                        if (isSongRestoredFromPrefs) goPreferences.latestPlayedSong?.second!! else 0

                }
            }
        }
    }

    //method to update info on controls panel
    private fun updatePlayingInfo(restore: Boolean) {

        val selectedSong = mMediaPlayerHolder.currentSong.first
        mSeekProgressBar.max = selectedSong?.duration!!.toInt()

        mPlayingSong.text = selectedSong.title

        mPlayingArtist.text =
            getString(
                R.string.artist_and_album,
                selectedSong.artist,
                selectedSong.album
            )

        updateRepeatStatus(false)

        if (isNowPlaying) updateNowPlayingInfo()

        if (restore) {

            if (!mMediaPlayerHolder.queueSongs.isNullOrEmpty() && !mMediaPlayerHolder.isQueueStarted)
                mMediaPlayerInterface.onQueueEnabled() else
                mMediaPlayerInterface.onQueueStartedOrEnded(mMediaPlayerHolder.isQueueStarted)

            updatePlayingStatus(false)

            if (::mPlayerService.isInitialized) {
                //stop foreground if coming from pause state
                mPlayerService.apply {
                    if (isRestoredFromPause) {
                        stopForeground(false)
                        musicNotificationManager.notificationManager.notify(
                            NOTIFICATION_ID,
                            mPlayerService.musicNotificationManager.notificationBuilder.build()
                        )
                        isRestoredFromPause = false
                    }
                }
            }
        }
    }

    private fun updateRepeatStatus(onPlaybackCompletion: Boolean) {
        if (isNowPlaying) {
            when {
                onPlaybackCompletion -> ThemeHelper.updateIconTint(mRepeatNP, mResolvedIconsColor)
                mMediaPlayerHolder.isRepeat -> ThemeHelper.updateIconTint(
                    mRepeatNP,
                    mResolvedAccentColor
                )
                else -> ThemeHelper.updateIconTint(mRepeatNP, mResolvedIconsColor)
            }
        }
    }

    private fun updateNowPlayingInfo() {

        val selectedSong = mMediaPlayerHolder.currentSong.first
        val selectedSongDuration = selectedSong?.duration!!

        mSongTextNP.text = selectedSong.title

        mArtistAlbumTextNP.text =
            getString(
                R.string.artist_and_album,
                selectedSong.artist,
                selectedSong.album
            )

        mSongSeekTextNP.text =
            MusicUtils.formatSongDuration(mMediaPlayerHolder.playerPosition.toLong(), false)
        mSongDurationTextNP.text = MusicUtils.formatSongDuration(selectedSongDuration, false)

        mSeekBarNP.max = selectedSong.duration.toInt()

        MusicUtils.getBitrate(MusicUtils.getContentUri(selectedSong.id!!), contentResolver)?.let {
            mRatesTextNP.text = getString(R.string.rates, it.first, it.second)
        }

        updatePlayingStatus(true)
    }

    fun openPlayingArtistAlbum(view: View) {
        if (isMediaPlayerHolder && mMediaPlayerHolder.isCurrentSong) {

            val selectedSong = mMediaPlayerHolder.currentSong.first
            val selectedArtistOrFolder = selectedSong?.artist
            if (sDetailsFragmentExpanded)
                mDetailsFragment.updateView(
                    this,
                    selectedArtistOrFolder,
                    MusicUtils.getPlayingAlbumPosition(selectedArtistOrFolder, mMediaPlayerHolder)
                )
            else
                openDetailsFragment(selectedArtistOrFolder, isFolder = false)

            if (isNowPlaying) mNowPlayingDialog.dismiss()
        }
    }

    override fun onCloseActivity() {
        if (isMediaPlayerHolder && mMediaPlayerHolder.isPlaying) Utils.stopPlaybackDialog(
            this,
            mMediaPlayerHolder
        ) else super.onBackPressed()
    }

    override fun onArtistOrFolderSelected(artistOrFolder: String, isFolder: Boolean) {
        openDetailsFragment(
            artistOrFolder,
            isFolder
        )
    }

    private fun startPlayback(song: Music?, album: List<Music>?) {
        if (isMediaPlayerHolder) {
            if (!mPlayerService.isRunning) startService(mBindingIntent)
            mMediaPlayerHolder.apply {
                setCurrentSong(song, album, false)
                initMediaPlayer(song)
            }
        }
    }

    override fun onSongSelected(song: Music?, songs: List<Music>?) {
        if (isMediaPlayerHolder) mMediaPlayerHolder.apply {
            isSongRestoredFromPrefs = false
            if (!isPlay) isPlay = true
            if (isQueue) setQueueEnabled(false)
            startPlayback(song, songs)
        }
    }

    private fun resumeOrPause() {
        if (checkIsPlayer(true)) mMediaPlayerHolder.resumeOrPause()
    }

    private fun skip(isNext: Boolean) {
        if (checkIsPlayer(true)) {
            if (!mMediaPlayerHolder.isPlay) mMediaPlayerHolder.isPlay = true
            if (mMediaPlayerHolder.isSongRestoredFromPrefs) mMediaPlayerHolder.isSongRestoredFromPrefs =
                false
            if (isNext) mMediaPlayerHolder.skip(true) else mMediaPlayerHolder.instantReset()
        }
    }

    private fun setRepeat() {
        if (checkIsPlayer(true)) {
            mMediaPlayerHolder.repeat()
            updateRepeatStatus(false)
        }
    }

    fun openEqualizer(view: View) {
        if (checkIsPlayer(true)) mMediaPlayerHolder.openEqualizer(this)
    }

    override fun onAddToQueue(song: Music) {
        if (checkIsPlayer(true)) {
            mMediaPlayerHolder.apply {
                if (queueSongs.isEmpty()) setQueueEnabled(true)
                queueSongs.add(song)
                Utils.makeToast(
                    this@MainActivity,
                    getString(
                        R.string.queue_song_add,
                        song.title
                    ),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    override fun onShuffleSongs(songs: MutableList<Music>?) {
        songs?.shuffle()
        val song = songs?.get(0)
        onSongSelected(song, songs)
    }

    fun openQueueDialog(view: View) {
        if (checkIsPlayer(false) && mMediaPlayerHolder.queueSongs.isNotEmpty())
            mQueueDialog = Utils.showQueueSongsDialog(this, mMediaPlayerHolder)
        else
            Utils.makeToast(
                this, getString(R.string.error_no_queue),
                Toast.LENGTH_SHORT
            )
    }

    fun openLovedSongsDialog(view: View) {
        if (!goPreferences.lovedSongs.isNullOrEmpty())
            Utils.showLovedSongsDialog(this, this, mMediaPlayerHolder)
        else
            Utils.makeToast(
                this, getString(R.string.error_no_loved_songs),
                Toast.LENGTH_SHORT
            )
    }

    //method to handle intent to play audio file from external app
    private fun handleIntent(intent: Intent) {

        intent.data?.let { returnUri ->
            contentResolver.query(returnUri, null, null, null, null)
        }?.use { cursor ->
            try {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                val song =
                    MusicUtils.getSongForIntent(cursor.getString(displayNameIndex))
                //get album songs and sort them
                val albumSongs = MusicUtils.getAlbumSongs(song?.artist, song?.album)

                onSongSelected(song, albumSongs)

            } catch (e: Exception) {
                e.printStackTrace()
                Utils.makeToast(
                    this@MainActivity,
                    getString(R.string.error_unknown_unsupported),
                    Toast.LENGTH_LONG
                )
                finishAndRemoveTask()
            }
        }
    }

    //interface to let MediaPlayerHolder update the UI media player controls.
    private val mMediaPlayerInterface = object : MediaPlayerInterface {

        override fun onPlaybackCompleted() {
            updateRepeatStatus(true)
        }

        override fun onUpdateRepeatStatus() {
            updateRepeatStatus(false)
        }

        override fun onClose() {
            //finish activity if visible
            finishAndRemoveTask()
        }

        override fun onPositionChanged(position: Int) {
            if (!sUserIsSeeking) {
                mSeekProgressBar.progress = position
                if (isNowPlaying) mSeekBarNP.progress = position
            }
        }

        override fun onStateChanged() {
            updatePlayingStatus(false)
            updatePlayingStatus(isNowPlaying)
            if (mMediaPlayerHolder.state != RESUMED && mMediaPlayerHolder.state != PAUSED) {
                updatePlayingInfo(false)
                if (::mQueueDialog.isInitialized && mQueueDialog.first.isShowing && mMediaPlayerHolder.isQueue)
                    mQueueDialog.second.swapSelectedSong(
                        mMediaPlayerHolder.currentSong.first
                    )
            }
        }

        override fun onQueueEnabled() {
            ThemeHelper.updateIconTint(
                mQueueButton,
                mResolvedIconsColor
            )
        }

        override fun onQueueCleared() {
            if (::mQueueDialog.isInitialized && mQueueDialog.first.isShowing) mQueueDialog.first.dismiss()
        }

        override fun onQueueStartedOrEnded(started: Boolean) {
            ThemeHelper.updateIconTint(
                mQueueButton,
                when {
                    started -> mResolvedAccentColor
                    mMediaPlayerHolder.isQueue -> mResolvedIconsColor
                    else -> mResolvedDisabledIconsColor
                }
            )
        }
    }

    //view pager's adapter
    private inner class ScreenSlidePagerAdapter(fm: FragmentManager) :
        FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            initFragmentAtIndex(mActiveFragments?.get(position)?.toInt()!!)
            return super.instantiateItem(container, position)
        }

        override fun getCount() = mActiveFragments?.size!!

        override fun getItem(position: Int) = handleOnNavigationItemSelected(position)!!
    }
}
