package com.iven.musicplayergo

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.google.android.material.tabs.TabLayout
import com.iven.musicplayergo.adapters.QueueAdapter
import com.iven.musicplayergo.fragments.*
import com.iven.musicplayergo.music.*
import com.iven.musicplayergo.player.*
import com.iven.musicplayergo.ui.ThemeHelper
import com.iven.musicplayergo.ui.UIControlInterface
import com.iven.musicplayergo.ui.Utils
import com.oze.music.musicbar.FixedMusicBar
import com.oze.music.musicbar.MusicBar
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.player_controls_panel.*
import kotlin.properties.Delegates

const val RESTORE_SETTINGS_FRAGMENT = "restore_settings_fragment_key"

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity(), UIControlInterface {

    //colors
    private var mResolvedAccentColor: Int by Delegates.notNull()
    private var mResolvedAlphaAccentColor: Int by Delegates.notNull()
    private var mResolvedIconsColor: Int by Delegates.notNull()
    private var mResolvedDisabledIconsColor: Int by Delegates.notNull()

    //fragments
    private var mArtistsFragment: ArtistsFragment? = null
    private var mAllMusicFragment: AllMusicFragment? = null
    private var mFoldersFragment: FoldersFragment? = null
    private var mSettingsFragment: SettingsFragment? = null

    private lateinit var mDetailsFragment: DetailsFragment
    private val sDetailsFragmentExpanded get() = ::mDetailsFragment.isInitialized && mDetailsFragment.isAdded
    private var sRevealAnimationRunning = false

    private var mActiveFragments: MutableList<String>? = null

    private var sRestoreSettingsFragment = false

    //views
    private lateinit var mViewPager: ViewPager
    private lateinit var mTabsLayout: TabLayout
    private lateinit var mPlayerControlsContainer: View

    private var sDeviceLand = false

    //settings/controls panel
    private lateinit var mPlayingArtist: TextView
    private lateinit var mPlayingSong: TextView
    private lateinit var mSeekProgressBar: ProgressBar
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mLovedSongsButton: ImageView
    private lateinit var mLoveSongsNumber: TextView
    private lateinit var mQueueButton: ImageView

    private var mControlsPaddingNoTabs: Int by Delegates.notNull()
    private var mControlsPaddingNormal: Int by Delegates.notNull()
    private var mControlsPaddingEnd: Int by Delegates.notNull()
    private val sTabsEnabled = goPreferences.isTabsEnabled

    //now playing
    private lateinit var mNowPlayingDialog: MaterialDialog
    private lateinit var mFixedMusicBar: FixedMusicBar
    private lateinit var mSongTextNP: TextView
    private lateinit var mArtistAlbumTextNP: TextView
    private lateinit var mSongSeekTextNP: TextView
    private lateinit var mSongDurationTextNP: TextView
    private lateinit var mSkipPrevButtonNP: ImageView
    private lateinit var mPlayPauseButtonNP: ImageView
    private lateinit var mSkipNextButtonNP: ImageView
    private lateinit var mRepeatNP: ImageView
    private lateinit var mVolumeSeekBarNP: SeekBar
    private lateinit var mLoveButtonNP: ImageView
    private lateinit var mVolumeNP: ImageView
    private lateinit var mRatesTextNP: TextView


    //music player things
    private var mAllDeviceSongs: MutableList<Music>? = null

    //booleans
    private var sUserIsSeeking = false

    //music
    private lateinit var mMusic: Map<String, List<Album>>

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

            getAllDeviceSongs()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            sBound = false
        }
    }

    private fun doBindService() {
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
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            RESTORE_SETTINGS_FRAGMENT,
            true
        )
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
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            Utils.showPermissionRationale(
                this
            ) else doBindService()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (goPreferences.isEdgeToEdge && !sDeviceLand && window != null) ThemeHelper.handleEdgeToEdge(
            window,
            mainView
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //set ui theme
        setTheme(ThemeHelper.getAccentedTheme().first)

        setContentView(R.layout.main_activity)

        //init views
        getViewsAndResources()

        setupControlsPanelSpecs()

        initMediaButtons()

        sDeviceLand = ThemeHelper.isDeviceLand(resources)

        sRestoreSettingsFragment =
            savedInstanceState?.getBoolean(RESTORE_SETTINGS_FRAGMENT) ?: intent.getBooleanExtra(
                RESTORE_SETTINGS_FRAGMENT,
                false
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) checkPermission() else doBindService()
    }

    private fun getViewsAndResources() {

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

    private fun getAllDeviceSongs() {

        val viewModel = ViewModelProviders.of(this).get(MusicViewModel::class.java)

        viewModel.getMutableLiveData(this).observe(this, Observer { allSongsUnfiltered ->

            if (!allSongsUnfiltered.isNullOrEmpty()) {

                mAllDeviceSongs = allSongsUnfiltered
                goPreferences.emergencySongsLib = allSongsUnfiltered

                buildLibraryAndFinishSetup()

            } else {
                if (!goPreferences.emergencySongsLib.isNullOrEmpty()) {

                    Utils.makeToast(
                        this@MainActivity,
                        getString(R.string.error_unknown),
                        Toast.LENGTH_LONG
                    )
                    mAllDeviceSongs = goPreferences.emergencySongsLib
                    buildLibraryAndFinishSetup()

                } else {

                    Utils.makeToast(
                        this@MainActivity,
                        getString(R.string.error_no_music),
                        Toast.LENGTH_LONG
                    )
                    finishAndRemoveTask()
                }
            }
        })
    }

    private fun buildLibraryAndFinishSetup() {

        val libraryViewModel = ViewModelProviders.of(this).get(LibraryViewModel::class.java)

        libraryViewModel.getMutableLiveData(this, mAllDeviceSongs)
            .observe(this, Observer { allAlbumByArtist ->

                mMusic = allAlbumByArtist

                handleRestoring()

                initViewPager()
            })
    }

    private fun handleRestoring() {
        //let's get intent from external app and open the song,
        //else restore the player (normal usage)
        if (intent != null && Intent.ACTION_VIEW == intent.action && intent.data != null)
            handleIntent(intent)
        else
            restorePlayerStatus()
    }

    private fun initViewPager() {

        mActiveFragments = goPreferences.activeFragments?.toMutableList()

        initActiveFragmentsOrTabs(true)

        val pagerAdapter = ScreenSlidePagerAdapter(supportFragmentManager)
        mViewPager.offscreenPageLimit = mActiveFragments?.size?.minus(1)!!
        mViewPager.adapter = pagerAdapter

        if (sRestoreSettingsFragment) mViewPager.currentItem = mViewPager.offscreenPageLimit

        if (sTabsEnabled) {
            mTabsLayout.apply {

                setupWithViewPager(mViewPager)

                tabIconTint = ColorStateList.valueOf(mResolvedAlphaAccentColor)
                initActiveFragmentsOrTabs(false)

                getTabAt(if (sRestoreSettingsFragment) mViewPager.currentItem else 0)?.icon?.setTint(
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
            if (onInitActiveFragments) initActiveFragments(it.toInt()) else
                mTabsLayout.getTabAt(mActiveFragments?.indexOf(it)!!)?.setIcon(
                    ThemeHelper.getTabIcon(
                        it.toInt()
                    )
                )
        }
    }

    private fun initActiveFragments(index: Int) {
        when (index) {
            0 -> if (mArtistsFragment == null || !mArtistsFragment?.isAdded!!) mArtistsFragment =
                ArtistsFragment.newInstance()
            1 -> if (mAllMusicFragment == null || !mAllMusicFragment?.isAdded!!) mAllMusicFragment =
                AllMusicFragment.newInstance()
            2 -> if (mFoldersFragment == null || !mFoldersFragment?.isAdded!!) mFoldersFragment =
                FoldersFragment.newInstance()
            else -> if (mSettingsFragment == null || !mSettingsFragment?.isAdded!!) mSettingsFragment =
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
                mDetailsFragment, DetailsFragment.TAG_ARTIST_FOLDER
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

        mPlayerControlsContainer.setOnLongClickListener {
            if (checkIsPlayer(true)) openPlayingArtistAlbum(it)
            return@setOnLongClickListener true
        }
    }

    /**
    UI related methods
     */
    private fun setFixedMusicBarProgressListener() {

        mFixedMusicBar.setProgressChangeListener(
            object : MusicBar.OnMusicBarProgressChangeListener {

                val defaultPositionColor = mSongSeekTextNP.currentTextColor
                var userSelectedPosition = 0

                override fun onProgressChanged(
                    musicBar: MusicBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (sUserIsSeeking) {
                        userSelectedPosition = musicBar.position
                        mSongSeekTextNP.setTextColor(
                            mResolvedAccentColor
                        )
                    }
                    mSongSeekTextNP.text =
                        MusicUtils.formatSongDuration(musicBar.position.toLong(), false)
                }

                override fun onStartTrackingTouch(musicBar: MusicBar?) {
                    sUserIsSeeking = true
                }

                override fun onStopTrackingTouch(musicBar: MusicBar) {
                    if (sUserIsSeeking) {
                        mSongSeekTextNP.setTextColor(defaultPositionColor)
                    }
                    sUserIsSeeking = false
                    if (mMediaPlayerHolder.state != PLAYING) {
                        mSeekProgressBar.progress = userSelectedPosition
                        mFixedMusicBar.setProgress(userSelectedPosition)
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
                mFixedMusicBar = customView.findViewById(R.id.np_fixed_music_bar)
                mFixedMusicBar.setBackgroundBarPrimeColor(
                    mResolvedAlphaAccentColor
                )

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

                setupPreciseVolumeHandler()
                setFixedMusicBarProgressListener()

                mRatesTextNP = customView.findViewById(R.id.np_rates)
                updateNowPlayingInfo()

                onDismiss {
                    mFixedMusicBar.removeAllListener()
                }
            }

            if (goPreferences.isEdgeToEdge && !sDeviceLand && mNowPlayingDialog.window != null) ThemeHelper.handleEdgeToEdge(
                mNowPlayingDialog.window,
                mNowPlayingDialog.view
            )
        }
    }

    override fun onAccentUpdated() {
        if (mMediaPlayerHolder.isPlaying) mMediaPlayerHolder.updateNotification()
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
                        if (isSongRestoredFromPrefs) goPreferences.latestPlayedSong?.first
                        else
                            musicLibrary.randomMusic

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

        mFixedMusicBar.loadFrom(selectedSong.path, selectedSong.duration.toInt())

        MusicUtils.getBitrate(selectedSong.path)?.let {
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

    /**
    Music player/playback related methods
     */
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

    @TargetApi(23)
    private fun checkPermission() {
        if (Utils.hasToShowPermissionRationale(this)) Utils.showPermissionRationale(this) else doBindService()
    }

    //method to handle intent to play audio file from external app
    private fun handleIntent(intent: Intent) {

        val uri = intent.data

        try {
            if (uri.toString().isNotEmpty()) {

                val path = MusicUtils.getRealPathFromURI(this, uri)

                //if we were able to get the song play it!
                if (MusicUtils.getSongForIntent(
                        path,
                        mAllDeviceSongs
                    ) != null
                ) {

                    val song =
                        MusicUtils.getSongForIntent(path, mAllDeviceSongs)

                    //get album songs and sort them
                    val albumSongs = MusicUtils.getAlbumSongs(song?.artist, song?.album)

                    onSongSelected(song, albumSongs)

                } else {
                    Utils.makeToast(
                        this@MainActivity,
                        getString(R.string.error_unknown_unsupported),
                        Toast.LENGTH_LONG
                    )
                    finishAndRemoveTask()
                }
            }
        } catch (e: Exception) {
            Utils.makeToast(
                this@MainActivity,
                getString(R.string.error_unknown_unsupported),
                Toast.LENGTH_LONG
            )
            finishAndRemoveTask()
        }
    }

    /**
     * Interface to let MediaPlayerHolder update the UI media player controls.
     */
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
                if (isNowPlaying) mFixedMusicBar.setProgress(position)
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

    /**
     * A simple pager adapter that represents 5 ScreenSlidePageFragment objects, in
     * sequence.
     */
    private inner class ScreenSlidePagerAdapter(fm: FragmentManager) :
        FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount() = mActiveFragments?.size!!

        override fun getItem(position: Int): Fragment {
            return handleOnNavigationItemSelected(position)!!
        }
    }
}
