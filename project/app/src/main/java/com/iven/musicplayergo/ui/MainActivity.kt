package com.iven.musicplayergo.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil.ImageLoader
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.setPeekHeight
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.callbacks.onShow
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.list.getListAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.R
import com.iven.musicplayergo.adapters.QueueAdapter
import com.iven.musicplayergo.databinding.*
import com.iven.musicplayergo.extensions.*
import com.iven.musicplayergo.fragments.*
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.helpers.*
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.player.EqualizerUtils
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.player.MediaPlayerInterface
import com.iven.musicplayergo.player.PlayerService
import de.halfbit.edgetoedge.Edge
import de.halfbit.edgetoedge.edgeToEdge


@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity(), UIControlInterface {

    // View binding classes
    private lateinit var mMainActivityBinding: MainActivityBinding
    private lateinit var mPlayerControlsPanelBinding: PlayerControlsPanelBinding

    // View model
    private val mMusicViewModel: MusicViewModel by viewModels()

    // Colors
    private val mResolvedAccentColor by lazy { ThemeHelper.resolveThemeAccent(this) }
    private val mResolvedAlphaAccentColor by lazy {
        ThemeHelper.getAlphaAccent(
                this,
                ThemeHelper.getAlphaForAccent()
        )
    }
    private val mResolvedIconsColor by lazy { R.color.widgetsColor.decodeColor(this) }

    private val mResolvedDisabledIconsColor
            by lazy {
                ThemeHelper.resolveColorAttr(
                        this,
                        android.R.attr.colorButtonNormal
                )
            }

    // Fragments
    private val mActiveFragments: List<Int> = goPreferences.activeFragments.toList()
    private var mArtistsFragment: MusicContainersListFragment? = null
    private var mAllMusicFragment: AllMusicFragment? = null
    private var mFoldersFragment: MusicContainersListFragment? = null
    private var mAlbumsFragment: MusicContainersListFragment? = null
    private var mSettingsFragment: SettingsFragment? = null
    private lateinit var mDetailsFragment: DetailsFragment
    private lateinit var mEqualizerFragment: EqFragment

    // Booleans
    private val sDetailsFragmentExpanded get() = supportFragmentManager.isFragment(GoConstants.DETAILS_FRAGMENT_TAG)
    private val sErrorFragmentExpanded get() = supportFragmentManager.isFragment(GoConstants.ERROR_FRAGMENT_TAG)
    private val sEqFragmentExpanded get() = supportFragmentManager.isFragment(GoConstants.EQ_FRAGMENT_TAG)
    private var sRevealAnimationRunning = false

    private var sAppearanceChanged = false
    private var sRestoreSettingsFragment = false
    private val sLandscape by lazy { ThemeHelper.isDeviceLand(resources) }

    // Now playing
    private lateinit var mNowPlayingDialog: MaterialDialog
    private lateinit var mNowPlayingBinding: NowPlayingBinding
    private lateinit var mNowPlayingControlsBinding: NowPlayingControlsBinding
    private lateinit var mNowPlayingExtendedControlsBinding: NowPlayingExtendedControlsBinding

    private val mDefaultCover by lazy {
        BitmapFactory.decodeResource(
                resources,
                R.drawable.album_art
        )
    }

    private val mImageLoader: ImageLoader by lazy {
        getImageLoader()
    }

    // Music player things
    private lateinit var mQueueDialog: MaterialDialog
    private lateinit var mQueueAdapter: QueueAdapter

    // The player
    private lateinit var mMediaPlayerHolder: MediaPlayerHolder
    private val isMediaPlayerHolder get() = ::mMediaPlayerHolder.isInitialized
    private val isNowPlaying get() = ::mNowPlayingDialog.isInitialized && mNowPlayingDialog.isShowing

    // Our PlayerService shit
    private lateinit var mPlayerService: PlayerService
    private var sBound = false
    private lateinit var mBindingIntent: Intent

    private fun checkIsPlayer(showError: Boolean) = mMediaPlayerHolder.apply {
        if (!isMediaPlayer && !isSongRestoredFromPrefs && showError) {
            getString(
                    R.string.error_bad_id
            ).toToast(
                    this@MainActivity
            )
        }
    }.isMediaPlayer

    // Defines callbacks for service binding, passed to bindService()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            val binder = service as PlayerService.LocalBinder
            mPlayerService = binder.getService()
            sBound = true
            mMediaPlayerHolder = mPlayerService.mediaPlayerHolder
            mMediaPlayerHolder.mediaPlayerInterface = mMediaPlayerInterface

            mMusicViewModel.deviceMusic.observe(this@MainActivity, { returnedMusic ->
                finishSetup(returnedMusic)
            })
            mMusicViewModel.getDeviceMusic()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            sBound = false
        }
    }

    private fun doBindService() {
        mMainActivityBinding.loadingProgressBar.handleViewVisibility(true)
        mBindingIntent = Intent(this, PlayerService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onBackPressed() {
        when {
            sDetailsFragmentExpanded -> closeDetailsFragment()
            sErrorFragmentExpanded -> finishAndRemoveTask()
            sEqFragmentExpanded -> closeEqualizerFragment()
            else -> if (mMainActivityBinding.viewPager2.currentItem != 0) {
                mMainActivityBinding.viewPager2.currentItem =
                        0
            } else {
                if (isMediaPlayerHolder && mMediaPlayerHolder.isPlaying) {
                    DialogHelper.stopPlaybackDialog(
                            this,
                            mMediaPlayerHolder
                    )
                } else {
                    onCloseActivity()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (sAppearanceChanged) {
            super.onSaveInstanceState(outState)
            outState.putBoolean(
                    GoConstants.RESTORE_SETTINGS_FRAGMENT,
                    true
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mMusicViewModel.cancel()
        if (sBound) {
            unbindService(connection)
        }
        if (isMediaPlayerHolder && !mMediaPlayerHolder.isPlaying && ::mPlayerService.isInitialized && mPlayerService.isRunning) {
            mPlayerService.stopForeground(true)
            stopService(mBindingIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) {
            mMediaPlayerHolder.onRestartSeekBarCallback()
        }
    }

    // Pause SeekBar callback
    override fun onPause() {
        super.onPause()
        if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) {
            saveSongToPref()
            mMediaPlayerHolder.apply {
                onPauseSeekBarCallback()
                if (!isPlaying) {
                    mMediaPlayerHolder.giveUpAudioFocus()
                }
            }
        }
    }

    // Manage request permission result
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            GoConstants.PERMISSION_REQUEST_READ_EXTERNAL_STORAGE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted, yay! Do bind service
                    doBindService()
                } else {
                    // Permission denied, boo! Error!
                    notifyError(GoConstants.TAG_NO_PERMISSION)
                }
            }
        }
    }

    override fun onDenyPermission() {
        notifyError(GoConstants.TAG_NO_PERMISSION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(ThemeHelper.getAccentedTheme().first)

        mMainActivityBinding = MainActivityBinding.inflate(layoutInflater)
        mPlayerControlsPanelBinding = PlayerControlsPanelBinding.bind(mMainActivityBinding.root)

        setContentView(mMainActivityBinding.root)

        if (goPreferences.isEdgeToEdge) {
            window?.apply {
                if (!VersioningHelper.isQ()) {
                    statusBarColor = Color.TRANSPARENT
                    navigationBarColor = Color.TRANSPARENT
                }
                ThemeHelper.handleLightSystemBars(resources.configuration, this)
            }
            edgeToEdge {
                mMainActivityBinding.root.fit { Edge.Top + Edge.Bottom }
            }
        }

        initMediaButtons()

        sRestoreSettingsFragment =
                savedInstanceState?.getBoolean(GoConstants.RESTORE_SETTINGS_FRAGMENT)
                        ?: intent.getBooleanExtra(
                                GoConstants.RESTORE_SETTINGS_FRAGMENT,
                                false
                        )

        if (PermissionsHelper.hasToAskForReadStoragePermission(this)) {
            PermissionsHelper.manageAskForReadStoragePermission(
                    activity = this, uiControlInterface = this
            )
        } else {
            doBindService()
        }
    }

    private fun notifyError(errorType: String) {
        mPlayerControlsPanelBinding.playerView.handleViewVisibility(false)
        mMainActivityBinding.loadingProgressBar.handleViewVisibility(false)
        mMainActivityBinding.viewPager2.handleViewVisibility(false)
        supportFragmentManager.addFragment(
                ErrorFragment.newInstance(errorType),
                GoConstants.ERROR_FRAGMENT_TAG,
                false
        )
    }

    private fun finishSetup(music: MutableList<Music>?) {

        if (!music.isNullOrEmpty()) {

            mMainActivityBinding.loadingProgressBar.handleViewVisibility(false)

            initViewPager()

            synchronized(handleRestore()) {
                mPlayerControlsPanelBinding.playerView.animate().apply {
                    duration = 500
                    alpha(1.0F)
                }
            }
        } else {
            notifyError(GoConstants.TAG_NO_MUSIC)
        }
    }

    // Handle restoring: handle intent data, if any, or restore playback
    private fun handleRestore() {
        if (intent != null && Intent.ACTION_VIEW == intent.action && intent.data != null) {
            handleIntent(
                    intent
            )
        } else {
            restorePlayerStatus()
        }
    }

    private fun initViewPager() {

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        mMainActivityBinding.viewPager2.offscreenPageLimit = mActiveFragments.size.minus(1)
        mMainActivityBinding.viewPager2.adapter = pagerAdapter

        mPlayerControlsPanelBinding.tabLayout.apply {

            tabIconTint = ColorStateList.valueOf(mResolvedAlphaAccentColor)

            mPlayerControlsPanelBinding.tabLayout.apply {
                TabLayoutMediator(this, mMainActivityBinding.viewPager2) { tab, position ->
                    val fragmentIndex = mActiveFragments[position]
                    tab.setIcon(ThemeHelper.getTabIcon(fragmentIndex))
                    initFragmentAtPosition(fragmentIndex)
                }.attach()
            }

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab) {
                    tab.icon?.setTint(mResolvedAccentColor)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    supportFragmentManager.goBackFromFragment(sDetailsFragmentExpanded)
                    supportFragmentManager.goBackFromFragment(sEqFragmentExpanded)
                    tab.icon?.setTint(mResolvedAlphaAccentColor)
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                }
            })

            getTabAt(
                    if (sRestoreSettingsFragment) {
                        mMainActivityBinding.viewPager2.offscreenPageLimit
                    } else {
                        0
                    }
            )?.icon?.setTint(
                    mResolvedAccentColor
            )
        }

        if (sRestoreSettingsFragment) {
            mMainActivityBinding.viewPager2.setCurrentItem(
                    mMainActivityBinding.viewPager2.offscreenPageLimit,
                    false
            )
        }
    }

    private fun initFragmentAtPosition(fragmentIndex: Int) {
        when (fragmentIndex) {
            0 -> if (mArtistsFragment == null) {
                mArtistsFragment =
                        MusicContainersListFragment.newInstance(GoConstants.ARTIST_VIEW)
            }
            1 -> if (mAlbumsFragment == null) {
                mAlbumsFragment =
                        MusicContainersListFragment.newInstance(GoConstants.ALBUM_VIEW)
            }
            2 -> if (mAllMusicFragment == null) {
                mAllMusicFragment =
                        AllMusicFragment.newInstance()
            }
            3 -> if (mFoldersFragment == null) {
                mFoldersFragment =
                        MusicContainersListFragment.newInstance(GoConstants.FOLDER_VIEW)
            }
            else -> if (mSettingsFragment == null) {
                mSettingsFragment =
                        SettingsFragment.newInstance()
            }
        }
    }

    private fun handleOnNavigationItemSelected(itemId: Int) = when (itemId) {
        0 -> getFragmentForIndex(mActiveFragments[0])
        1 -> getFragmentForIndex(mActiveFragments[1])
        2 -> getFragmentForIndex(mActiveFragments[2])
        3 -> getFragmentForIndex(mActiveFragments[3])
        else -> getFragmentForIndex(mActiveFragments[4])
    }

    private fun getFragmentForIndex(index: Int) = when (index) {
        0 -> mArtistsFragment
        1 -> mAlbumsFragment
        2 -> mAllMusicFragment
        3 -> mFoldersFragment
        else -> mSettingsFragment
    }

    private fun openDetailsFragment(
            selectedArtistOrFolder: String?,
            launchedBy: String
    ) {
        if (!sDetailsFragmentExpanded) {
            if (sEqFragmentExpanded) {
                mEqualizerFragment.setDisableCircleReveal()
            }
            mDetailsFragment =
                    DetailsFragment.newInstance(
                            selectedArtistOrFolder,
                            launchedBy,
                            MusicOrgHelper.getPlayingAlbumPosition(
                                    selectedArtistOrFolder,
                                    mMediaPlayerHolder,
                                    mMusicViewModel.deviceAlbumsByArtist
                            )
                    )
            supportFragmentManager.addFragment(mDetailsFragment, GoConstants.DETAILS_FRAGMENT_TAG, sEqFragmentExpanded)
        }
    }

    private fun closeDetailsFragment() {
        if (!sRevealAnimationRunning) {
            mDetailsFragment.onHandleBackPressed().apply {
                sRevealAnimationRunning = true
                doOnEnd {
                    synchronized(supportFragmentManager.goBackFromFragment(true)) {
                        sRevealAnimationRunning = false
                    }
                }
            }
        }
    }

    private fun closeNowPlayingDialog() {
        if (::mNowPlayingDialog.isInitialized && mNowPlayingDialog.isShowing) {
            mNowPlayingDialog.dismiss()
        }
    }

    private fun initMediaButtons() {

        mPlayerControlsPanelBinding.playPauseButton.setOnClickListener { resumeOrPause() }

        mPlayerControlsPanelBinding.queueButton.setOnLongClickListener {
            if (checkIsPlayer(true) && mMediaPlayerHolder.isQueue) {
                DialogHelper.showClearQueueDialog(
                        this,
                        mMediaPlayerHolder
                )
            }
            return@setOnLongClickListener true
        }

        mPlayerControlsPanelBinding.lovedSongsButton.setOnLongClickListener {
            if (!goPreferences.lovedSongs.isNullOrEmpty()) {
                DialogHelper.showClearLovedSongDialog(
                        this,
                        this
                )
            }
            return@setOnLongClickListener true
        }

        onLovedSongsUpdate(false)

        mPlayerControlsPanelBinding.playingSongsContainer.setOnLongClickListener { playerControlsContainer ->
            if (checkIsPlayer(true)) {
                openPlayingArtistAlbum(playerControlsContainer)
            }
            return@setOnLongClickListener true
        }
    }

    private fun setSeekBarProgressListener() {

        mNowPlayingBinding.npSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    val defaultPositionColor = mNowPlayingBinding.npSeek.currentTextColor
                    var userSelectedPosition = 0
                    var isUserSeeking = false

                    override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        if (isUserSeeking) {
                            userSelectedPosition = progress
                            mNowPlayingBinding.npSeek.setTextColor(
                                    mResolvedAccentColor
                            )
                        }
                        mNowPlayingBinding.npSeek.text =
                                progress.toLong().toFormattedDuration(isAlbum = false, isSeekBar = true)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        if (isUserSeeking) {
                            mNowPlayingBinding.npSeek.setTextColor(defaultPositionColor)
                            mMediaPlayerHolder.onPauseSeekBarCallback()
                            isUserSeeking = false
                        }
                        if (mMediaPlayerHolder.state != GoConstants.PLAYING) {
                            mPlayerControlsPanelBinding.songProgress.progress = userSelectedPosition
                            mNowPlayingBinding.npSeekBar.progress = userSelectedPosition
                        }
                        mMediaPlayerHolder.seekTo(
                                userSelectedPosition,
                                updatePlaybackStatus = mMediaPlayerHolder.isPlaying,
                                restoreProgressCallBack = !isUserSeeking
                        )
                    }
                })
    }

    private fun setCovers() {
        if (goPreferences.isCovers) {
            if (!ThemeHelper.isDeviceLand(resources)) {
                mNowPlayingBinding.npArtistAlbum.textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
                mNowPlayingBinding.npSong.textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
            }
            mNowPlayingBinding.npCover.loadCover(
                    mImageLoader,
                    mMediaPlayerHolder.currentSong.first,
                    mDefaultCover,
                    isCircleCrop = true,
                    isLoadDelay = false
            )
        } else {
            mNowPlayingBinding.npCover.handleViewVisibility(false)
        }
    }

    private fun setupPreciseVolumeHandler() {

        mMediaPlayerHolder.currentVolumeInPercent.apply {
            mNowPlayingExtendedControlsBinding.npVolume.setImageResource(
                    ThemeHelper.getPreciseVolumeIcon(
                            this
                    )
            )
            mNowPlayingExtendedControlsBinding.npVolumeSeek.progress = this
        }

        mNowPlayingExtendedControlsBinding.npVolumeSeek.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {

            var isUserSeeking = false

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                if (isUserSeeking) {

                    mMediaPlayerHolder.setPreciseVolume(progress)

                    mNowPlayingExtendedControlsBinding.npVolume.setImageResource(
                            ThemeHelper.getPreciseVolumeIcon(
                                    progress
                            )
                    )

                    ThemeHelper.updateIconTint(
                            mNowPlayingExtendedControlsBinding.npVolume,
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
                        mNowPlayingExtendedControlsBinding.npVolume,
                        mResolvedIconsColor
                )
            }
        })
    }

    fun openNowPlaying(view: View) {

        if (checkIsPlayer(true) && mMediaPlayerHolder.isCurrentSong) {

            mNowPlayingDialog = MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {

                customView(R.layout.now_playing)

                mNowPlayingBinding = NowPlayingBinding.bind(getCustomView())
                mNowPlayingControlsBinding = NowPlayingControlsBinding.bind(mNowPlayingBinding.root)
                mNowPlayingExtendedControlsBinding =
                        NowPlayingExtendedControlsBinding.bind(mNowPlayingBinding.root)

                mNowPlayingBinding.npSong.isSelected = true
                mNowPlayingBinding.npArtistAlbum.isSelected = true

                mNowPlayingControlsBinding.npSkipPrev.setOnClickListener { skip(false) }
                mNowPlayingControlsBinding.npSkipPrev.setOnLongClickListener {
                    fastSeek(false)
                    return@setOnLongClickListener true
                }

                mNowPlayingControlsBinding.npPlay.setOnClickListener { resumeOrPause() }

                mNowPlayingControlsBinding.npSkipNext.setOnClickListener { skip(true) }
                mNowPlayingControlsBinding.npSkipNext.setOnLongClickListener {
                    fastSeek(true)
                    return@setOnLongClickListener true
                }

                setCovers()

                mNowPlayingControlsBinding.npRepeat.setImageResource(
                        ThemeHelper.getRepeatIcon(
                                mMediaPlayerHolder
                        )
                )

                ThemeHelper.updateIconTint(
                        mNowPlayingControlsBinding.npRepeat,
                        if (mMediaPlayerHolder.isRepeat1X || mMediaPlayerHolder.isLooping) {
                            mResolvedAccentColor
                        } else {
                            mResolvedIconsColor
                        }
                )

                mNowPlayingControlsBinding.npRepeat.setOnClickListener { setRepeat() }

                mNowPlayingExtendedControlsBinding.npLove.setOnClickListener {
                    ListsHelper.addToLovedSongs(
                            this@MainActivity,
                            mMediaPlayerHolder.currentSong.first,
                            mMediaPlayerHolder.playerPosition,
                            mMediaPlayerHolder.launchedBy
                    )
                    onLovedSongsUpdate(false)
                }

                if (goPreferences.isPreciseVolumeEnabled) {
                    setupPreciseVolumeHandler()
                } else {
                    mNowPlayingExtendedControlsBinding.npVolumeSeek.isEnabled = false
                    ThemeHelper.updateIconTint(
                            mNowPlayingExtendedControlsBinding.npVolume,
                            mResolvedDisabledIconsColor
                    )
                }

                setSeekBarProgressListener()

                updateNowPlayingInfo()

                onShow {
                    mNowPlayingBinding.npSeekBar.progress =
                            mPlayerControlsPanelBinding.songProgress.progress
                }

                onDismiss {
                    mNowPlayingBinding.npSeekBar.setOnSeekBarChangeListener(null)
                }

                if (goPreferences.isEdgeToEdge && !sLandscape) {
                    window?.apply {
                        ThemeHelper.handleLightSystemBars(
                                this@MainActivity.resources.configuration,
                                this
                        )
                        edgeToEdge {
                            mNowPlayingBinding.root.fit { Edge.Bottom }
                        }
                    }
                }

                // to ensure full dialog's height
                getCustomView().afterMeasured {
                    mNowPlayingDialog.setPeekHeight(height)
                }
            }
        }
    }

    private fun saveSongToPref() {
        if (::mMediaPlayerHolder.isInitialized && !mMediaPlayerHolder.isPlaying || mMediaPlayerHolder.state == GoConstants.PAUSED) mMediaPlayerHolder.apply {
            MusicOrgHelper.saveLatestSong(currentSong.first, mMediaPlayerHolder, launchedBy)
        }
    }

    override fun onThemeChanged() {
        sAppearanceChanged = true
        synchronized(saveSongToPref()) {
            AppCompatDelegate.setDefaultNightMode(
                    ThemeHelper.getDefaultNightMode(
                            this
                    )
            )
        }
    }

    override fun onAppearanceChanged(isAccentChanged: Boolean, restoreSettings: Boolean) {
        sAppearanceChanged = true
        synchronized(saveSongToPref()) {
            ThemeHelper.applyChanges(this)
        }
    }

    private fun updatePlayingStatus(isNowPlaying: Boolean) {
        val isPlaying = mMediaPlayerHolder.state != GoConstants.PAUSED
        val drawable =
                if (isPlaying) {
                    R.drawable.ic_pause
                } else {
                    R.drawable.ic_play
                }
        if (isNowPlaying) {
            mNowPlayingControlsBinding.npPlay.setImageResource(drawable)
        } else {
            mPlayerControlsPanelBinding.playPauseButton.setImageResource(
                    drawable
            )
        }
    }

    override fun onLovedSongsUpdate(clear: Boolean) {

        val lovedSongs = goPreferences.lovedSongs

        if (clear) {
            lovedSongs?.clear()
            goPreferences.lovedSongs = lovedSongs
        }

        val lovedSongsButtonColor = if (lovedSongs.isNullOrEmpty()) {
            mResolvedDisabledIconsColor
        } else {
            R.color.red.decodeColor(this)
        }
        ThemeHelper.updateIconTint(
                mPlayerControlsPanelBinding.lovedSongsButton,
                lovedSongsButtonColor
        )
    }

    private fun restorePlayerStatus() {
        if (isMediaPlayerHolder) {
            // If we are playing and the activity was restarted
            // update the controls panel
            mMediaPlayerHolder.apply {

                if (isMediaPlayer && mMediaPlayerHolder.isPlaying) {

                    onRestartSeekBarCallback()
                    updatePlayingInfo(true)

                } else {

                    isSongRestoredFromPrefs = goPreferences.latestPlayedSong != null

                    val song =
                            if (isSongRestoredFromPrefs) {
                                MusicOrgHelper.getSongForRestore(
                                        goPreferences.latestPlayedSong,
                                        mMusicViewModel.deviceMusicList
                                )
                            } else {
                                mMusicViewModel.randomMusic
                            }

                    val songs = MusicOrgHelper.getAlbumSongs(
                            song.artist,
                            song.album,
                            mMusicViewModel.deviceAlbumsByArtist
                    )

                    if (!songs.isNullOrEmpty()) {

                        isPlay = false

                        startPlayback(
                                song,
                                songs,
                                getLatestSongLaunchedBy()
                        )

                        updatePlayingInfo(false)

                        mPlayerControlsPanelBinding.songProgress.progress =
                                if (isSongRestoredFromPrefs) {
                                    goPreferences.latestPlayedSong?.startFrom!!
                                } else {
                                    0
                                }
                    } else {
                        notifyError(GoConstants.TAG_SD_NOT_READY)
                    }
                }
                onUpdateDefaultAlbumArt(mDefaultCover)
            }
        }
    }

    private fun getLatestSongLaunchedBy() = goPreferences.latestPlayedSong?.launchedBy
            ?: GoConstants.ARTIST_VIEW

    // method to update info on controls panel
    private fun updatePlayingInfo(restore: Boolean) {

        val selectedSong = mMediaPlayerHolder.currentSong.first

        mPlayerControlsPanelBinding.songProgress.progress = 0
        mPlayerControlsPanelBinding.songProgress.max = selectedSong?.duration!!.toInt()

        mPlayerControlsPanelBinding.playingSong.text = selectedSong.title

        mPlayerControlsPanelBinding.playingArtist.text =
                getString(
                        R.string.artist_and_album,
                        selectedSong.artist,
                        selectedSong.album
                )

        updateRepeatStatus(false)

        if (isNowPlaying) {
            updateNowPlayingInfo()
        }

        if (restore) {

            if (!mMediaPlayerHolder.queueSongs.isNullOrEmpty() && !mMediaPlayerHolder.isQueueStarted) {
                mMediaPlayerInterface.onQueueEnabled()
            } else {
                mMediaPlayerInterface.onQueueStartedOrEnded(mMediaPlayerHolder.isQueueStarted)
            }

            updatePlayingStatus(false)

            if (::mPlayerService.isInitialized) {
                //stop foreground if coming from pause state
                mPlayerService.apply {
                    if (isRestoredFromPause) {
                        stopForeground(false)
                        musicNotificationManager.updateNotification()
                        isRestoredFromPause = false
                    }
                }
            }
        }
    }

    private fun updateRepeatStatus(onPlaybackCompletion: Boolean) {
        if (isNowPlaying) {
            mNowPlayingControlsBinding.npRepeat.setImageResource(
                    ThemeHelper.getRepeatIcon(
                            mMediaPlayerHolder
                    )
            )
            when {
                onPlaybackCompletion -> ThemeHelper.updateIconTint(
                        mNowPlayingControlsBinding.npRepeat,
                        mResolvedIconsColor
                )
                mMediaPlayerHolder.isRepeat1X or mMediaPlayerHolder.isLooping -> {
                    ThemeHelper.updateIconTint(
                            mNowPlayingControlsBinding.npRepeat,
                            mResolvedAccentColor
                    )
                }
                else -> ThemeHelper.updateIconTint(
                        mNowPlayingControlsBinding.npRepeat,
                        mResolvedIconsColor
                )
            }
        }
    }

    private fun updateNowPlayingInfo() {

        val selectedSong = mMediaPlayerHolder.currentSong.first
        val selectedSongDuration = selectedSong?.duration!!

        mNowPlayingBinding.npSong.text = selectedSong.title

        mNowPlayingBinding.npArtistAlbum.text =
                getString(
                        R.string.artist_and_album,
                        selectedSong.artist,
                        selectedSong.album
                )

        mNowPlayingBinding.npSeek.text =
                mMediaPlayerHolder.playerPosition.toLong().toFormattedDuration(false, isSeekBar = true)
        mNowPlayingBinding.npDuration.text =
                selectedSongDuration.toFormattedDuration(false, isSeekBar = true)

        mNowPlayingBinding.npSeekBar.max = selectedSong.duration.toInt()

        selectedSong.id?.toContentUri()?.toBitrate(this)?.let { bitrateInfo ->
            mNowPlayingBinding.npRates.text =
                    getString(R.string.rates, bitrateInfo.first, bitrateInfo.second)
        }

        updatePlayingStatus(true)
    }

    private fun getSongSource(selectedSong: Music?, launchedBy: String): String? {
        return when (launchedBy) {
            GoConstants.FOLDER_VIEW -> selectedSong?.relativePath
            GoConstants.ARTIST_VIEW -> selectedSong?.artist
            else -> selectedSong?.album
        }
    }

    fun openPlayingArtistAlbum(view: View) {
        if (isMediaPlayerHolder && mMediaPlayerHolder.isCurrentSong) {

            val isPlayingFromFolder = mMediaPlayerHolder.launchedBy
            val selectedSong = mMediaPlayerHolder.currentSong.first
            val selectedArtistOrFolder = getSongSource(selectedSong, isPlayingFromFolder)
            if (sDetailsFragmentExpanded) {
                if (mDetailsFragment.hasToUpdate(selectedArtistOrFolder)) {
                    synchronized(supportFragmentManager.goBackFromFragment(true)) {
                        openDetailsFragment(
                                selectedArtistOrFolder,
                                mMediaPlayerHolder.launchedBy
                        )
                    }
                } else {
                    mDetailsFragment.tryToSnapToAlbumPosition(
                            MusicOrgHelper.getPlayingAlbumPosition(
                                    selectedArtistOrFolder,
                                    mMediaPlayerHolder,
                                    mMusicViewModel.deviceAlbumsByArtist
                            )
                    )
                }
            } else {
                openDetailsFragment(selectedArtistOrFolder, mMediaPlayerHolder.launchedBy)
            }

            closeNowPlayingDialog()
        }
    }

    override fun onCloseActivity() {
        if (isMediaPlayerHolder && mMediaPlayerHolder.isPlaying) {
            DialogHelper.stopPlaybackDialog(
                    this,
                    mMediaPlayerHolder
            )
        } else {
            finishAndRemoveTask()
        }
    }

    override fun onHandleFocusPref() {
        if (isMediaPlayerHolder) {
            if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) {
                if (goPreferences.isFocusEnabled) {
                    mMediaPlayerHolder.tryToGetAudioFocus()
                } else {
                    mMediaPlayerHolder.giveUpAudioFocus()
                }
            }
        }
    }

    override fun onHandleNotificationUpdate(isAdditionalActionsChanged: Boolean) {
        if (isMediaPlayerHolder) {
            mMediaPlayerHolder.onHandleNotificationUpdate(isAdditionalActionsChanged)
        }
    }

    override fun onArtistOrFolderSelected(artistOrFolder: String, launchedBy: String) {
        openDetailsFragment(
                artistOrFolder,
                launchedBy
        )
    }

    private fun startPlayback(song: Music?, songs: List<Music>?, launchedBy: String) {
        if (isMediaPlayerHolder) {
            if (::mPlayerService.isInitialized && !mPlayerService.isRunning) {
                startService(
                        mBindingIntent
                )
            }
            mMediaPlayerHolder.apply {
                setCurrentSong(song, songs, isFromQueue = false, isFolderAlbum = launchedBy)
                initMediaPlayer(song)
            }
        }
    }

    override fun onSongSelected(song: Music?, songs: List<Music>?, launchedBy: String) {
        if (isMediaPlayerHolder) {
            mMediaPlayerHolder.apply {
                isSongRestoredFromPrefs = false
                if (!isPlay) {
                    isPlay = true
                }
                if (isQueue) {
                    setQueueEnabled(false)
                }
                startPlayback(song, songs, launchedBy)
            }
        }
    }

    private fun resumeOrPause() {
        if (checkIsPlayer(true)) {
            mMediaPlayerHolder.resumeOrPause()
        }
    }

    private fun fastSeek(isForward: Boolean) {
        if (checkIsPlayer(true)) {
            mMediaPlayerHolder.fastSeek(isForward)
        }
    }

    private fun skip(isNext: Boolean) {
        if (checkIsPlayer(true)) {
            if (!mMediaPlayerHolder.isPlay) {
                mMediaPlayerHolder.isPlay = true
            }
            if (isNext) {
                mMediaPlayerHolder.skip(true)
            } else {
                mMediaPlayerHolder.instantReset()
            }
            if (mMediaPlayerHolder.isSongRestoredFromPrefs) {
                mMediaPlayerHolder.isSongRestoredFromPrefs =
                        false
            }
        }
    }

    private fun setRepeat() {
        if (checkIsPlayer(true)) {
            mMediaPlayerHolder.repeat(mMediaPlayerHolder.isPlaying)
            updateRepeatStatus(false)
        }
    }

    override fun onGetEqualizer(): Triple<Equalizer, BassBoost, Virtualizer> = mMediaPlayerHolder.getEqualizer()

    override fun onEnableEqualizer(isEnabled: Boolean) {
        if (::mMediaPlayerHolder.isInitialized) {
            mMediaPlayerHolder.setEqualizerEnabled(isEnabled)
        }
    }

    override fun onSaveEqualizerSettings(selectedPreset: Int, bassBoost: Short, virtualizer: Short) {
        mMediaPlayerHolder.onSaveEqualizerSettings(selectedPreset, bassBoost, virtualizer)
    }

    fun openEqualizer(view: View) {
        if (checkIsPlayer(true)) {
            if (!EqualizerUtils.hasEqualizer(this)) {
                if (!sEqFragmentExpanded) {
                    mEqualizerFragment = mMediaPlayerHolder.openEqualizerCustom()
                    synchronized(closeNowPlayingDialog()) {
                        if (sDetailsFragmentExpanded) {
                            mDetailsFragment.setDisableCircleReveal()
                        }
                        supportFragmentManager.addFragment(mEqualizerFragment, GoConstants.EQ_FRAGMENT_TAG, sDetailsFragmentExpanded)
                    }
                }
            } else {
                mMediaPlayerHolder.openEqualizer(this)
            }
        }
    }

    private fun closeEqualizerFragment() {
        if (!sRevealAnimationRunning) {
            mEqualizerFragment.onHandleBackPressed().apply {
                sRevealAnimationRunning = true
                doOnEnd {
                    synchronized(supportFragmentManager.goBackFromFragment(true)) {
                        sRevealAnimationRunning = false
                    }
                }
            }
        }
    }

    override fun onAddToQueue(song: Music?) {
        if (checkIsPlayer(true)) {
            mMediaPlayerHolder.apply {
                if (queueSongs.isEmpty()) {
                    setQueueEnabled(true)
                }
                song?.let { songToQueue ->
                    queueSongs.add(songToQueue)
                    getString(
                            R.string.queue_song_add,
                            songToQueue.title
                    ).toToast(
                            this@MainActivity
                    )
                }
            }
        }
    }

    override fun onShuffleSongs(songs: MutableList<Music>?, launchedBy: String) {
        val randomNumber = (0 until songs?.size!!).getRandom()
        songs.shuffle()
        val song = songs[randomNumber]
        onSongSelected(song, songs, launchedBy)
    }

    override fun onAddToFilter(stringToFilter: String?) {
        stringToFilter?.let { string ->
            mArtistsFragment?.onListFiltered(string)
            mAlbumsFragment?.onListFiltered(string)
            mFoldersFragment?.onListFiltered(string)
            ListsHelper.addToHiddenItems(string)
        }
    }

    fun openQueueDialog(view: View) {
        if (checkIsPlayer(false) && mMediaPlayerHolder.queueSongs.isNotEmpty()) {
            mQueueDialog = DialogHelper.showQueueSongsDialog(this, mMediaPlayerHolder)
            mQueueAdapter = mQueueDialog.getListAdapter() as QueueAdapter
        } else {
            getString(R.string.error_no_queue).toToast(this)
        }
    }

    fun openLovedSongsDialog(view: View) {
        if (!goPreferences.lovedSongs.isNullOrEmpty()) {
            DialogHelper.showLovedSongsDialog(
                    this,
                    this,
                    mMediaPlayerHolder,
                    mMusicViewModel.deviceMusicList,
                    mMusicViewModel.deviceAlbumsByArtist
            )
        } else {
            getString(R.string.error_no_loved_songs).toToast(this)
        }
    }

    //method to handle intent to play audio file from external app
    private fun handleIntent(intent: Intent) {

        intent.data?.let { returnUri ->
            contentResolver.query(returnUri, null, null, null, null)
        }?.use { cursor ->
            try {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                val song = mMusicViewModel.getSongFromIntent(cursor.getString(displayNameIndex))
                //get album songs and sort them
                val albumSongs = MusicOrgHelper.getAlbumSongs(
                        song?.artist,
                        song?.album,
                        mMusicViewModel.deviceAlbumsByArtist
                )

                onSongSelected(song, albumSongs, GoConstants.ARTIST_VIEW)

            } catch (e: Exception) {
                e.printStackTrace()
                notifyError(GoConstants.TAG_NO_MUSIC_INTENT)
            }
        }
    }

    // interface to let MediaPlayerHolder update the UI media player controls.
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
            mPlayerControlsPanelBinding.songProgress.progress = position
            if (isNowPlaying) {
                mNowPlayingBinding.npSeekBar.progress = position
            }
        }

        override fun onStateChanged() {
            updatePlayingStatus(false)
            updatePlayingStatus(isNowPlaying)
            if (mMediaPlayerHolder.state != GoConstants.RESUMED && mMediaPlayerHolder.state != GoConstants.PAUSED) {
                updatePlayingInfo(false)
                if (::mQueueDialog.isInitialized && mQueueDialog.isShowing && mMediaPlayerHolder.isQueue) {
                    mQueueAdapter.swapSelectedSong(
                            mMediaPlayerHolder.currentSong.first
                    )
                }
            }
        }

        override fun onQueueEnabled() {
            ThemeHelper.updateIconTint(
                    mPlayerControlsPanelBinding.queueButton,
                    mResolvedIconsColor
            )
        }

        override fun onQueueCleared() {
            if (::mQueueDialog.isInitialized && mQueueDialog.isShowing) {
                mQueueDialog.dismiss()
            }
        }

        override fun onQueueStartedOrEnded(started: Boolean) {
            ThemeHelper.updateIconTint(
                    mPlayerControlsPanelBinding.queueButton,
                    when {
                        started -> mResolvedAccentColor
                        mMediaPlayerHolder.isQueue -> mResolvedIconsColor
                        else -> mResolvedDisabledIconsColor
                    }
            )
        }

        override fun onFocusLoss() {
            saveSongToPref()
        }

        override fun onSaveSong() {
            saveSongToPref()
        }

        override fun onPlaylistEnded() {
            getString(R.string.error_list_ended).toToast(this@MainActivity)
        }
    }

    // ViewPager2 adapter class
    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = mActiveFragments.size

        override fun createFragment(position: Int): Fragment =
                handleOnNavigationItemSelected(position)!!
    }
}
