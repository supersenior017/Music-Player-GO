package com.iven.musicplayergo

import android.Manifest
import android.animation.Animator
import android.annotation.TargetApi
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.view.Menu
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.iven.musicplayergo.adapters.AlbumsAdapter
import com.iven.musicplayergo.adapters.ArtistsAdapter
import com.iven.musicplayergo.adapters.ColorsAdapter
import com.iven.musicplayergo.adapters.SongsAdapter
import com.iven.musicplayergo.music.Album
import com.iven.musicplayergo.music.Music
import com.iven.musicplayergo.music.MusicUtils
import com.iven.musicplayergo.music.MusicViewModel
import com.iven.musicplayergo.player.*
import com.iven.musicplayergo.uihelpers.UIUtils
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import kotlinx.android.synthetic.main.artist_details.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.player_controls_panel.*
import kotlinx.android.synthetic.main.player_seek.*
import kotlinx.android.synthetic.main.player_settings.*
import kotlinx.android.synthetic.main.search_toolbar.*
import kotlin.math.hypot

private const val TAG_PERMISSION_RATIONALE = "com.iven.musicplayergo.rationale"

class MainActivity : AppCompatActivity() {

    ////preferences
    private var sThemeInverted: Boolean = false
    private var mAccent: Int = R.color.blue
    private var sSearchEnabled: Boolean = true


    ////views

    //indicator fast scroller by reddit
    private lateinit var mIndicatorFastScrollerView: FastScrollerView
    private lateinit var mIndicatorFastScrollThumb: FastScrollerThumbView

    //RecyclerViews
    private lateinit var mArtistsRecyclerView: RecyclerView
    private lateinit var mAlbumsRecyclerView: RecyclerView
    private lateinit var mSongsRecyclerView: RecyclerView

    private lateinit var mArtistsAdapter: ArtistsAdapter
    private lateinit var mAlbumsAdapter: AlbumsAdapter
    private lateinit var mSongsAdapter: SongsAdapter

    private lateinit var mArtistsLayoutManager: LinearLayoutManager
    private lateinit var mAlbumsLayoutManager: LinearLayoutManager
    private lateinit var mSongsLayoutManager: LinearLayoutManager

    private lateinit var mSavedArtistRecyclerLayoutState: Parcelable
    private lateinit var mSavedAlbumsRecyclerLayoutState: Parcelable
    private lateinit var mSavedSongsRecyclerLayoutState: Parcelable

    //search bar
    private lateinit var mSupportActionBar: ActionBar

    //settings/controls panel
    private lateinit var mControlsContainer: LinearLayout
    private lateinit var mColorsRecyclerView: RecyclerView
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var mPlayerInfoView: View
    private lateinit var mPlayingAlbum: TextView
    private lateinit var mPlayingSong: TextView
    private lateinit var mSeekBar: SeekBar
    private lateinit var mSongPosition: TextView
    private lateinit var mSongDuration: TextView
    private lateinit var mSkipPrevButton: ImageView
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mSkipNextButton: ImageView
    private lateinit var mSearchToggleButton: ImageView

    //artists details
    private lateinit var mArtistDetails: LinearLayout
    private lateinit var mArtistDetailsTitle: TextView
    private lateinit var mArtistsDetailsDiscCount: TextView
    private lateinit var mArtistsDetailsSelectedDisc: TextView
    private lateinit var mArtistDetailsSelectedDiscYear: TextView


    ////music player things

    //view model
    private lateinit var mViewModel: MusicViewModel
    private lateinit var mAllDeviceSongs: MutableList<Music>

    //booleans
    private var sUserIsSeeking = false
    private var sArtistDiscographyExpanded: Boolean = false
    private var sBound: Boolean = false

    //music
    private lateinit var mMusic: Map<String, Map<String, List<Music>>>
    private lateinit var mArtists: MutableList<String>
    private lateinit var mSelectedArtistSongs: MutableList<Music>
    private lateinit var mSelectedArtistAlbums: List<Album>
    private var mNavigationArtist: String? = "unknown"

    //player
    private lateinit var mPlayerService: PlayerService
    private lateinit var mMediaPlayerHolder: MediaPlayerHolder
    private lateinit var mMusicNotificationManager: MusicNotificationManager

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            mPlayerService = (iBinder as PlayerService.LocalBinder).instance
            mMediaPlayerHolder = mPlayerService.mediaPlayerHolder!!
            mMediaPlayerHolder.mediaPlayerInterface = mediaPlayerInterface

            mMusicNotificationManager = mPlayerService.musicNotificationManager
            mMusicNotificationManager.accent = UIUtils.getColor(this@MainActivity, mAccent, R.color.blue)
            loadMusic()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
        }
    }

    private fun doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        val startNotStickyIntent = Intent(this, PlayerService::class.java)
        bindService(startNotStickyIntent, mConnection, Context.BIND_AUTO_CREATE)
        sBound = true
        startService(startNotStickyIntent)
    }

    private fun doUnbindService() {
        if (sBound) {
            // Detach our existing connection.
            unbindService(mConnection)
            sBound = false
        }
    }

    //unbind service on destroy
    override fun onDestroy() {
        super.onDestroy()
        doUnbindService()
    }

    //restore recycler views state
    override fun onResume() {
        super.onResume()
        if (::mSavedArtistRecyclerLayoutState.isInitialized && ::mSavedAlbumsRecyclerLayoutState.isInitialized && ::mSavedSongsRecyclerLayoutState.isInitialized) {
            mArtistsLayoutManager.onRestoreInstanceState(mSavedArtistRecyclerLayoutState)
            mAlbumsLayoutManager.onRestoreInstanceState(mSavedAlbumsRecyclerLayoutState)
            mSongsLayoutManager.onRestoreInstanceState(mSavedSongsRecyclerLayoutState)
        }
        if (::mMediaPlayerHolder.isInitialized && mMediaPlayerHolder.isMediaPlayer) mMediaPlayerHolder.onResumeActivity()
    }

    //save recycler views state
    override fun onPause() {
        super.onPause()
        if (::mMediaPlayerHolder.isInitialized && mMediaPlayerHolder.isMediaPlayer) mMediaPlayerHolder.onPauseActivity()
        if (::mArtistsLayoutManager.isInitialized && ::mAlbumsLayoutManager.isInitialized && ::mSongsLayoutManager.isInitialized) {
            mSavedArtistRecyclerLayoutState = mArtistsLayoutManager.onSaveInstanceState()!!
            mSavedAlbumsRecyclerLayoutState = mAlbumsLayoutManager.onSaveInstanceState()!!
            mSavedSongsRecyclerLayoutState = mSongsLayoutManager.onSaveInstanceState()!!
        }
    }

    //manage bottom panel state on back pressed
    override fun onBackPressed() {
        when (mBottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_EXPANDED -> {
                mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            else -> {
                if (sArtistDiscographyExpanded) revealArtistDetails(false) else super.onBackPressed()
            }
        }
    }

    //inflate search menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (::mArtistsAdapter.isInitialized) {
            menuInflater.inflate(R.menu.search_menu, menu)

            val search = menu!!.findItem(R.id.search)
            val searchView = search.actionView as SearchView

            searchView.setIconifiedByDefault(false)
            UIUtils.setupSearch(searchView, mArtistsAdapter, mArtists, mIndicatorFastScrollerView)
        }
        return super.onCreateOptionsMenu(menu)
    }

    //manage request permission result, continue loading ui if permissions was granted
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) showPermissionRationale() else initMusicPlayerGO()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //set ui theme
        sThemeInverted = mMusicPlayerGoPreferences.isThemeInverted
        mAccent = mMusicPlayerGoPreferences.accent
        setTheme(UIUtils.resolveTheme(sThemeInverted, mMusicPlayerGoPreferences.accent))

        setContentView(R.layout.main_activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) checkPermission() else initMusicPlayerGO()
    }

    private fun initMusicPlayerGO() {

        //init everything!!!
        sSearchEnabled = mMusicPlayerGoPreferences.isSearchBarEnabled

        setViews()

        mControlsContainer.afterMeasured {
            container.setPadding(0, 0, 0, height)
            mBottomSheetBehavior.peekHeight = height
        }
        setupPlayerControls()
        setupSettings()
        initializeSeekBar()
        doBindService()
    }

    @TargetApi(23)
    private fun checkPermission() {

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            showPermissionRationale() else initMusicPlayerGO()
    }

    private fun showPermissionRationale() {
        PermissionDialogFragment.newInstance().show(supportFragmentManager, TAG_PERMISSION_RATIONALE)
    }

    fun openGitPage(@Suppress("UNUSED_PARAMETER") view: View) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/enricocid/Music-Player-GO")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadMusic() {

        mViewModel = ViewModelProviders.of(this).get(MusicViewModel::class.java)

        mViewModel.getMusic(MusicUtils.getMusicCursor(contentResolver)!!)
            .observe(this, Observer<Pair<MutableList<Music>, Map<String, Map<String, List<Music>>>>> { music ->
                mAllDeviceSongs = music.first
                mMusic = music.second
                if (mMusic.isNotEmpty()) {
                    setArtistsRecyclerView()
                    restorePlayerStatus()
                } else {
                    Toast.makeText(this, getString(R.string.error_no_music), Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
    }

    private fun setViews() {

        //set custom color to background
        main.setBackgroundColor(
            ColorUtils.setAlphaComponent(
                UIUtils.getColor(this, mAccent, R.color.blue),
                if (sThemeInverted) 10 else 40
            )
        )

        //indicator fast scroller view
        mIndicatorFastScrollerView = fastscroller
        mIndicatorFastScrollThumb = fastscroller_thumb

        //recycler views
        mArtistsRecyclerView = artists_rv
        mAlbumsRecyclerView = albums_rv
        mSongsRecyclerView = songs_rv
        mColorsRecyclerView = colors_rv

        //controls panel
        mControlsContainer = controls_container
        mBottomSheetBehavior = BottomSheetBehavior.from(design_bottom_sheet)
        mPlayerInfoView = player_info
        mPlayingSong = playing_song
        mPlayingAlbum = playing_album
        mSeekBar = seekTo
        mSongPosition = song_position
        mSongDuration = duration
        mSkipPrevButton = skip_prev_button
        mPlayPauseButton = play_pause_button
        mSkipNextButton = skip_next_button
        mSearchToggleButton = search_option

        //setup horizontal scrolling text
        UIUtils.setHorizontalScrollBehavior(mPlayerInfoView, playing_song, playing_album)

        //search view
        setSupportActionBar(search_toolbar)
        if (supportActionBar != null) {
            mSupportActionBar = supportActionBar!!
        }

        if (!sSearchEnabled) {
            mSupportActionBar.hide()
        }

        //artist details
        mArtistDetails = artist_details
        mArtistDetailsTitle = selected_discography_artist
        mArtistsDetailsDiscCount = selected_artist_album_count
        mArtistsDetailsSelectedDisc = selected_disc

        //setup horizontal scrolling text for artist details title and album title
        UIUtils.setHorizontalScrollBehavior(discs_artist_container, mArtistDetailsTitle)
        UIUtils.setHorizontalScrollBehavior(disc_title_container, mArtistsDetailsSelectedDisc)

        mArtistDetailsSelectedDiscYear = selected_disc_year
        close_button.setOnClickListener { revealArtistDetails(!sArtistDiscographyExpanded) }
    }

    private fun setupPlayerControls() {
        mPlayerInfoView.setOnClickListener { handlePlayerInfo() }
        mSkipPrevButton.setOnClickListener { skipPrev() }
        mSkipPrevButton.setOnLongClickListener {
            setRepeat()
            return@setOnLongClickListener false
        }
        mPlayPauseButton.setOnClickListener { resumeOrPause() }
        mSkipNextButton.setOnClickListener { skipNext() }
        shuffle_button.setOnClickListener {
            if (::mMediaPlayerHolder.isInitialized) {
                if (!mSeekBar.isEnabled) mSeekBar.isEnabled = true
                mSongsAdapter.randomPlaySelectedAlbum(mMediaPlayerHolder)
            }
        }
    }

    private fun initializeSeekBar() {
        mSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            val defaultPositionColor = mSongPosition.currentTextColor
            var userSelectedPosition = 0

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                sUserIsSeeking = true
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    userSelectedPosition = progress
                    mSongPosition.setTextColor(UIUtils.getColor(this@MainActivity, mAccent, R.color.blue))
                }
                mSongPosition.text = MusicUtils.formatSongDuration(progress.toLong())
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (sUserIsSeeking) {
                    mSongPosition.setTextColor(defaultPositionColor)
                }
                sUserIsSeeking = false
                mMediaPlayerHolder.seekTo(userSelectedPosition)
            }
        })
    }

    private fun setupSettings() {

        shuffle_option.setOnClickListener { shuffleSongs() }
        eq_option.setOnClickListener { openEqualizer() }
        mSearchToggleButton.setOnClickListener { handleSearchBarVisibility() }
        invert_option.setOnClickListener {
            invertTheme()
        }

        if (!sSearchEnabled) search_option.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)

        mColorsRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        val colorsAdapter = ColorsAdapter(this, mAccent)
        mColorsRecyclerView.adapter = colorsAdapter

        colorsAdapter.onColorClick = { accent ->
            mMusicNotificationManager.accent = UIUtils.getColor(this, mAccent, R.color.blue)
            if (mMediaPlayerHolder.isMediaPlayer) {
                mMusicNotificationManager.notificationManager.notify(
                    NOTIFICATION_ID,
                    mMusicNotificationManager.createNotification()
                )
            }
            mMusicPlayerGoPreferences.accent = accent
            UIUtils.applyNewThemeSmoothly(this)
        }
    }

    private fun continueLoadingOnArtistsConfigured() {
        //set indexes if artists rv is scrollable
        mArtistsRecyclerView.afterMeasured {
            if (mArtistsRecyclerView.computeVerticalScrollRange() > height) {

                mIndicatorFastScrollerView.setupWithRecyclerView(
                    mArtistsRecyclerView,
                    { position ->
                        val item = mArtists[position] // Get your model object
                        // or fetch the section at [position] from your database
                        FastScrollItemIndicator.Text(
                            item.substring(0, 1).toUpperCase() // Grab the first letter and capitalize it
                        ) // Return a text indicator
                    }
                )

                mIndicatorFastScrollerView.textColor =
                    ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, mAccent))
                mIndicatorFastScrollerView.afterMeasured {

                    //set margin for artists recycler to improve fast scroller visibility
                    mArtistsRecyclerView.setPadding(0,0,width,0)

                    //set margin for thumb view
                    val newLayoutParams = mIndicatorFastScrollThumb.layoutParams as FrameLayout.LayoutParams
                    newLayoutParams.marginEnd = width
                    mIndicatorFastScrollThumb.layoutParams = newLayoutParams
                }
                mIndicatorFastScrollThumb.setupWithFastScroller(mIndicatorFastScrollerView)
            } else {
                mIndicatorFastScrollerView.visibility = View.GONE
            }
            //set artist details on artists rv loaded
            setArtistDetails()
        }
    }

    private fun setArtistsRecyclerView() {

        mArtists = MusicUtils.getArtists(mMusic)
        mNavigationArtist = mArtists[0]

        //set the search menu
        invalidateOptionsMenu()
        //setHasOptionsMenu(sSearchEnabled)

        //set the artists list
        mArtistsRecyclerView.setHasFixedSize(true)

        mArtistsLayoutManager = LinearLayoutManager(this)
        mArtistsRecyclerView.layoutManager = mArtistsLayoutManager
        mArtistsAdapter = ArtistsAdapter(resources, mArtists, mMusic)

        mArtistsRecyclerView.adapter = mArtistsAdapter

        mArtistsAdapter.onArtistClick = { artist ->
            if (mNavigationArtist != artist) {
                mNavigationArtist = artist
                setArtistDetails()
                revealArtistDetails(true)
            } else {
                revealArtistDetails(true)
            }
        }

        continueLoadingOnArtistsConfigured()
    }

    private fun setArtistDetails() {

        val notSortedArtistDiscs = mMusic.getValue(mNavigationArtist!!)
        mSelectedArtistAlbums = MusicUtils.buildSortedArtistAlbums(resources, notSortedArtistDiscs)

        //set the titles and subtitles
        mArtistDetailsTitle.text = mNavigationArtist
        mArtistsDetailsDiscCount.text = getString(
            R.string.artist_info,
            mSelectedArtistAlbums.size,
            MusicUtils.getArtistSongsCount(notSortedArtistDiscs)
        )

        //set the albums list
        //one-time adapter initialization
        mAlbumsRecyclerView.setHasFixedSize(true)
        mAlbumsLayoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        mAlbumsRecyclerView.layoutManager = mAlbumsLayoutManager
        mAlbumsAdapter = AlbumsAdapter(
            mSelectedArtistAlbums,
            UIUtils.getColor(this, mAccent, R.color.blue)
        )
        mAlbumsRecyclerView.adapter = mAlbumsAdapter

        mAlbumsAdapter.onAlbumClick = { album ->
            setAlbumSongs(album)
        }

        mSelectedArtistSongs = MusicUtils.getArtistSongs(notSortedArtistDiscs)

        val placeholderAlbum = mSelectedArtistAlbums[0]
        setAlbumSongs(placeholderAlbum.title)
    }

    private fun setAlbumSongs(selectedAlbum: String) {
        val album = mMusic.getValue(mNavigationArtist!!).getValue(selectedAlbum)
        mArtistsDetailsSelectedDisc.text = selectedAlbum
        mArtistDetailsSelectedDiscYear.text = MusicUtils.getYearForAlbum(resources, album[0].year)

        //set the songs list
        if (!::mSongsAdapter.isInitialized) {
            //one-time adapter initialization
            mSongsRecyclerView.setHasFixedSize(true)
            mSongsLayoutManager = LinearLayoutManager(this)
            mSongsRecyclerView.layoutManager = mSongsLayoutManager
            mSongsAdapter = SongsAdapter(album.toMutableList())
            mSongsRecyclerView.adapter = mSongsAdapter
        } else {
            mSongsAdapter.swapSongs(album.toMutableList())
        }
        mSongsRecyclerView.setPadding(0, 0, 0, -resources.getDimensionPixelSize(R.dimen.songs_card_margin_bottom))
        mSongsAdapter.onSongClick = { music ->
            if (!mSeekBar.isEnabled) mSeekBar.isEnabled = true
            mMediaPlayerHolder.setCurrentSong(music, album)
            mMediaPlayerHolder.initMediaPlayer(music)
        }
    }

    private fun shuffleSongs() {
        if (::mMediaPlayerHolder.isInitialized) {
            val songs = if (sArtistDiscographyExpanded) mSelectedArtistSongs else mAllDeviceSongs
            songs.shuffle()
            val song = songs[0]
            mMediaPlayerHolder.setCurrentSong(song, songs)
            mMediaPlayerHolder.initMediaPlayer(song)
        }
    }

    private fun setRepeat() {
        if (checkIsPlayer()) {
            mMediaPlayerHolder.reset()
            updateResetStatus(false)
        }
    }

    private fun skipPrev() {
        if (checkIsPlayer()) {
            mMediaPlayerHolder.instantReset()
            if (mMediaPlayerHolder.isReset) {
                mMediaPlayerHolder.reset()
                updateResetStatus(false)
            }
        }
    }

    private fun resumeOrPause() {
        if (checkIsPlayer()) {
            mMediaPlayerHolder.resumeOrPause()
        }
    }

    private fun skipNext() {
        if (checkIsPlayer()) {
            mMediaPlayerHolder.skip(true)
        }
    }

    //interface to let MediaPlayerHolder update the UI media player controls
    val mediaPlayerInterface = object : MediaPlayerInterface {
        override fun onPlaybackCompleted() {
            updateResetStatus(true)
        }

        override fun onPositionChanged(position: Int) {
            if (!sUserIsSeeking) {
                mSeekBar.progress = position
            }
        }

        override fun onStateChanged() {
            updatePlayingStatus()
            if (mMediaPlayerHolder.state != RESUMED && mMediaPlayerHolder.state != PAUSED) {
                updatePlayingInfo(false)
            }
        }
    }

    private fun restorePlayerStatus() {
        if (::mMediaPlayerHolder.isInitialized) {
            mSeekBar.isEnabled = mMediaPlayerHolder.isMediaPlayer
            //if we are playing and the activity was restarted
            //update the controls panel
            if (mMediaPlayerHolder.isMediaPlayer) {
                mMediaPlayerHolder.onResumeActivity()
                updatePlayingInfo(true)
            }
        }
    }

    //method to update info on controls panel
    private fun updatePlayingInfo(restore: Boolean) {

        val selectedSong = mMediaPlayerHolder.currentSong
        val duration = selectedSong!!.duration
        mSeekBar.max = duration.toInt()
        mSongDuration.text = MusicUtils.formatSongDuration(duration)
        mPlayingSong.text =
            MusicUtils.buildSpanned(getString(R.string.playing_song, selectedSong.artist, selectedSong.title))
        mPlayingAlbum.text = selectedSong.album

        if (restore) {
            mSongPosition.text = MusicUtils.formatSongDuration(mMediaPlayerHolder.playerPosition.toLong())
            mSeekBar.progress = mMediaPlayerHolder.playerPosition

            updatePlayingStatus()
            updateResetStatus(false)

            //stop foreground if coming from pause state
            if (mPlayerService.isRestoredFromPause) {
                mPlayerService.stopForeground(false)
                mPlayerService.musicNotificationManager.notificationManager.notify(
                    NOTIFICATION_ID,
                    mPlayerService.musicNotificationManager.notificationBuilder!!.build()
                )
                mPlayerService.isRestoredFromPause = false
            }
        }
    }

    private fun updateResetStatus(onPlaybackCompletion: Boolean) {
        val themeColor = if (sThemeInverted) R.color.white else R.color.black
        val color = if (onPlaybackCompletion) themeColor else if (mMediaPlayerHolder.isReset) mAccent else themeColor
        mSkipPrevButton.setColorFilter(
            UIUtils.getColor(
                this,
                color,
                if (onPlaybackCompletion) themeColor else R.color.blue
            ), PorterDuff.Mode.SRC_IN
        )
    }

    private fun updatePlayingStatus() {
        val drawable = if (mMediaPlayerHolder.state != PAUSED) R.drawable.ic_pause else R.drawable.ic_play
        mPlayPauseButton.setImageResource(drawable)
    }

    private fun checkIsPlayer(): Boolean {
        val isPlayer = mMediaPlayerHolder.isMediaPlayer
        if (!isPlayer) {
            EqualizerUtils.notifyNoSessionId(this)
        }
        return isPlayer
    }

    private fun openEqualizer() {
        if (EqualizerUtils.hasEqualizer(this)) {
            if (checkIsPlayer()) {
                mMediaPlayerHolder.openEqualizer(this)
            }
        } else {
            Toast.makeText(this, getString(R.string.no_eq), Toast.LENGTH_SHORT).show()
        }
    }

    //method to reveal/hide artist details, it is a simple reveal animation
    private fun revealArtistDetails(show: Boolean) {

        val viewToRevealHeight = mArtistsRecyclerView.height
        val viewToRevealWidth = mAlbumsRecyclerView.width
        val viewToRevealHalfWidth = viewToRevealWidth / 2
        val radius = hypot(viewToRevealWidth.toDouble(), viewToRevealHeight.toDouble()).toFloat()
        val fromY = mArtistsRecyclerView.top / 2
        val startRadius = if (show) 0f else radius
        val finalRadius = if (show) radius else 0f

        val anim = ViewAnimationUtils.createCircularReveal(
            mArtistDetails,
            viewToRevealHalfWidth,
            fromY,
            startRadius,
            finalRadius
        )
        anim.duration = 500
        anim.addListener(revealAnimationListener(show))
        anim.start()
    }

    //reveal animation
    private fun revealAnimationListener(show: Boolean): Animator.AnimatorListener {

        return object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {
                if (show) {
                    sArtistDiscographyExpanded = true
                    mArtistDetails.visibility = View.VISIBLE
                    mArtistsRecyclerView.visibility = View.INVISIBLE
                    mArtistDetails.isClickable = false
                    mSearchToggleButton.visibility = View.GONE
                    mIndicatorFastScrollerView.visibility = View.GONE
                    if (sSearchEnabled && ::mSupportActionBar.isInitialized && mSupportActionBar.isShowing) mSupportActionBar.hide()
                }
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (!show) {
                    sArtistDiscographyExpanded = false
                    mArtistDetails.visibility = View.INVISIBLE
                    mArtistsRecyclerView.visibility = View.VISIBLE
                    mArtistDetails.isClickable = true
                    if (sSearchEnabled && ::mSupportActionBar.isInitialized && !mSupportActionBar.isShowing) mSupportActionBar.show()
                    mSearchToggleButton.visibility = View.VISIBLE
                    mIndicatorFastScrollerView.visibility = View.VISIBLE
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationRepeat(animation: Animator?) {
            }
        }
    }

    //method to handle player info click
    private fun handlePlayerInfo() {
        //if we are playing a song the go to the played artist/album details
        if (::mMediaPlayerHolder.isInitialized && mMediaPlayerHolder.currentSong != null) {
            val currentSong = mMediaPlayerHolder.currentSong
            val album = currentSong!!.album
            val artist = currentSong.artist
            //do only if we are not on played artist/album details
            if (mNavigationArtist != artist) {
                mArtistsAdapter.onArtistClick?.invoke(artist!!)
                val playingAlbumPosition = MusicUtils.getAlbumPositionInList(album!!, mSelectedArtistAlbums)
                mAlbumsAdapter.swapSelectedAlbum(playingAlbumPosition)
                mAlbumsRecyclerView.scrollToPosition(playingAlbumPosition)
                mAlbumsAdapter.onAlbumClick?.invoke(album)
            } else {
                revealArtistDetails(!sArtistDiscographyExpanded)
            }
        } else {
            revealArtistDetails(!sArtistDiscographyExpanded)
        }
    }

    //handle theme changes
    private fun invertTheme() {
        //avoid service killing when the player is in paused state
        if (::mMediaPlayerHolder.isInitialized && mMediaPlayerHolder.isPlaying) {
            if (mMediaPlayerHolder.state == PAUSED) {
                mPlayerService.startForeground(
                    NOTIFICATION_ID,
                    mPlayerService.musicNotificationManager.createNotification()
                )
                mPlayerService.isRestoredFromPause = true
            }
        }

        mMusicPlayerGoPreferences.isThemeInverted = !sThemeInverted
        UIUtils.applyNewThemeSmoothly(this)
    }

    //hide/show search bar dynamically
    private fun handleSearchBarVisibility() {
        if (::mSupportActionBar.isInitialized) {
            val newVisibility = !mSupportActionBar.isShowing
            mMusicPlayerGoPreferences.isSearchBarEnabled = newVisibility

            val searchToggleButtonColor = when (newVisibility) {
                false -> Color.GRAY
                true -> if (sThemeInverted) Color.WHITE else Color.BLACK
            }
            mSearchToggleButton.setColorFilter(searchToggleButtonColor, PorterDuff.Mode.SRC_IN)
            if (mSupportActionBar.isShowing) {
                mSupportActionBar.hide()
            } else {
                mSupportActionBar.show()
            }
            sSearchEnabled = newVisibility
        }
    }

    //viewTreeObserver extension to measure layout params
    //https://antonioleiva.com/kotlin-ongloballayoutlistener/
    private inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    f()
                }
            }
        })
    }
}
