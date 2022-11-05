package com.iven.musicplayergo.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.NowPlayingBinding
import com.iven.musicplayergo.databinding.NowPlayingControlsBinding
import com.iven.musicplayergo.databinding.NowPlayingCoverBinding
import com.iven.musicplayergo.databinding.NowPlayingVolControlBinding
import com.iven.musicplayergo.extensions.*
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.ui.MediaControlInterface
import com.iven.musicplayergo.ui.UIControlInterface
import com.iven.musicplayergo.utils.Lists
import com.iven.musicplayergo.utils.Popups
import com.iven.musicplayergo.utils.Theming
import com.iven.musicplayergo.utils.Versioning


class NowPlaying: BottomSheetDialogFragment() {

    private var _nowPlayingBinding: NowPlayingBinding? = null
    private var _npCoverBinding: NowPlayingCoverBinding? = null
    private var _npControlsBinding: NowPlayingControlsBinding? = null
    private var _npExtControlsBinding: NowPlayingVolControlBinding? = null

    var onNowPlayingCancelled: (() -> Unit)? = null

    private var mAlbumIdNp : Long? = -1L

    private lateinit var mMediaControlInterface: MediaControlInterface
    private lateinit var mUIControlInterface: UIControlInterface

    private val mMediaPlayerHolder get() = MediaPlayerHolder.getInstance()

    private val mGoPreferences get() = GoPreferences.getPrefsInstance()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mMediaControlInterface = activity as MediaControlInterface
            mUIControlInterface = activity as UIControlInterface
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _nowPlayingBinding = NowPlayingBinding.inflate(inflater, container, false).apply {
            _npCoverBinding = NowPlayingCoverBinding.bind(root)
            _npControlsBinding = NowPlayingControlsBinding.bind(root)
            _npExtControlsBinding = NowPlayingVolControlBinding.bind(root)
        }
        return _nowPlayingBinding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onNowPlayingCancelled?.invoke()
        _nowPlayingBinding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
    }

    private fun setupView() {
        _nowPlayingBinding?.run {
            npSong.isSelected = true
            npArtistAlbum.isSelected = true
            setupNPCoverLayout()
            with(npPlayingSongContainer) {
                contentDescription = getString(R.string.open_details_fragment)
                setOnClickListener {
                    mUIControlInterface.onOpenPlayingArtistAlbum()
                    dismiss()
                }
                setOnLongClickListener {
                    Toast.makeText(requireContext(), R.string.open_details_fragment, Toast.LENGTH_SHORT)
                        .show()
                    return@setOnLongClickListener false
                }
            }
        }

        _npControlsBinding?.run {
            npSkipPrev.setOnClickListener { skip(isNext = false) }
            npFastRewind.setOnClickListener { mMediaPlayerHolder.fastSeek(isForward = false) }
            npPlay.setOnClickListener { mMediaPlayerHolder.resumeOrPause() }
            npSkipNext.setOnClickListener { skip(isNext = true) }
            npFastForward.setOnClickListener { mMediaPlayerHolder.fastSeek(isForward = true) }
        }

        setupPreciseVolumeHandler()
        setupSeekBarProgressListener()
        updateNpInfo()

        with(mMediaPlayerHolder) {
            (currentSongFM ?: currentSong)?.let { song ->
                loadNpCover(song)
                _nowPlayingBinding?.npSeek?.text =
                    playerPosition.toLong().toFormattedDuration(isAlbum = false, isSeekBar = true)
                _nowPlayingBinding?.npSeekBar?.progress = playerPosition
                dialog.applyFullHeightDialog(requireActivity())
            }
        }
    }

    private fun setupNPCoverLayout() {

        if (!Theming.isDeviceLand(resources)) {
            _nowPlayingBinding?.npArtistAlbum?.textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
            _nowPlayingBinding?.npSong?.textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
        }

        _npCoverBinding?.npCover?.afterMeasured {
            val ratio = ResourcesCompat.getFloat(resources, R.dimen.cover_ratio)
            val dim = (width * ratio).toInt()
            layoutParams = LinearLayout.LayoutParams(dim, dim)
        }

        mMediaPlayerHolder.let { mph ->
            _npCoverBinding?.run {
                if (Versioning.isMarshmallow()) {
                    setupNPCoverButtonsToasts(npPlaybackSpeed)
                    npPlaybackSpeed.setOnClickListener { view ->
                        Popups.showPopupForPlaybackSpeed(requireActivity(), view)
                    }
                } else {
                    npPlaybackSpeed.visibility = View.GONE
                }

                npCover.background.alpha = Theming.getAlbumCoverAlpha(requireContext())
                npSaveTime.setOnClickListener { saveSongPosition() }
                npEqualizer.setOnClickListener { mUIControlInterface.onOpenEqualizer() }
                npLove.setOnClickListener {
                    Lists.addToFavorites(
                        requireContext(),
                        mph.currentSong,
                        canRemove = true,
                        0,
                        mph.launchedBy)
                    mUIControlInterface.onFavoritesUpdated(clear = false)
                    mph.onUpdateFavorites()
                    updateNpFavoritesIcon()
                }

                with(npRepeat) {
                    setImageResource(
                        Theming.getRepeatIcon(isNotification = false)
                    )
                    updateIconTint(
                        if (mph.isRepeat1X || mph.isLooping) {
                            Theming.resolveThemeColor(resources)
                        } else {
                            Theming.resolveWidgetsColorNormal(requireContext())
                        }
                    )
                    setOnClickListener { setRepeat() }
                    setupNPCoverButtonsToasts(npSaveTime, npLove, npEqualizer, this)
                }

                with (npPauseOnEnd) {
                    isChecked = mGoPreferences.continueOnEnd
                    setOnCheckedChangeListener { _, isChecked ->
                        mGoPreferences.continueOnEnd = isChecked
                        var msg = R.string.pause_on_end_disabled
                        if (isChecked) msg = R.string.pause_on_end
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT)
                            .show()
                    }
                    setOnLongClickListener { switch ->
                        Toast.makeText(requireContext(), switch.contentDescription, Toast.LENGTH_SHORT)
                            .show()
                        return@setOnLongClickListener true
                    }
                }
            }
        }
    }

    private fun setupNPCoverButtonsToasts(vararg imageButtons: ImageButton) {
        val iterator = imageButtons.iterator()
        while (iterator.hasNext()) {
            iterator.next().setOnLongClickListener { btn ->
                Toast.makeText(requireContext(), btn.contentDescription, Toast.LENGTH_SHORT)
                    .show()
                return@setOnLongClickListener true
            }
        }
    }

    private fun setupPreciseVolumeHandler() {

        _npExtControlsBinding?.run {

            val isVolumeEnabled = mGoPreferences.isPreciseVolumeEnabled
            npVolumeContainer.handleViewVisibility(show = isVolumeEnabled)

            if (isVolumeEnabled) {

                with(mMediaPlayerHolder.currentVolumeInPercent) {
                    npVolume.setImageResource(Theming.getPreciseVolumeIcon(this))
                    npVolumeSeek.progress = this
                    npVolumeValue.text = this.toString().padStart(3, '0')
                }

                npVolumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                    val defaultValueColor = _npExtControlsBinding?.npVolumeValue?.currentTextColor
                    val selectedColor = Theming.resolveThemeColor(resources)

                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mMediaPlayerHolder.setPreciseVolume(progress)
                            npVolumeValue.text = progress.toString().padStart(3, '0')
                            npVolume.setImageResource(
                                Theming.getPreciseVolumeIcon(progress)
                            )
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        npVolumeValue.setTextColor(selectedColor)
                        npVolume.updateIconTint(selectedColor)
                    }
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        npVolumeValue.setTextColor(defaultValueColor!!)
                        npVolume.updateIconTint(defaultValueColor)
                    }
                })
            }
        }
    }

    private fun setupSeekBarProgressListener() {

        mMediaPlayerHolder.let { mph ->
            _nowPlayingBinding?.run {
                npSeekBar.setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {

                        val defaultPositionColor = npSeek.currentTextColor
                        val selectedColor = Theming.resolveThemeColor(resources)
                        var userSelectedPosition = 0
                        var isUserSeeking = false

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            if (fromUser) userSelectedPosition = progress
                            npSeek.text =
                                progress.toLong().toFormattedDuration(isAlbum = false, isSeekBar = true)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            isUserSeeking = true
                            npSeek.setTextColor(selectedColor)
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            if (isUserSeeking) {
                                npSeek.setTextColor(defaultPositionColor)
                                mph.onPauseSeekBarCallback()
                                isUserSeeking = false
                            }
                            if (mph.state != GoConstants.PLAYING) {
                                mMediaControlInterface.onUpdatePositionFromNP(userSelectedPosition)
                                npSeekBar.progress = userSelectedPosition
                            }
                            mph.seekTo(
                                userSelectedPosition,
                                updatePlaybackStatus = mph.isPlaying,
                                restoreProgressCallBack = !isUserSeeking
                            )
                        }
                    })
            }
        }
    }

    fun updateRepeatStatus(onPlaybackCompletion: Boolean) {
        if (::mMediaControlInterface.isInitialized) {
            with(mMediaPlayerHolder) {
                val widgetsColorDisabled = Theming.resolveWidgetsColorNormal(requireContext())
                _npCoverBinding?.npRepeat?.let { rpBtn ->
                    rpBtn.setImageResource(Theming.getRepeatIcon(isNotification = false))
                    when {
                        onPlaybackCompletion -> rpBtn.updateIconTint(widgetsColorDisabled)
                        isRepeat1X or isLooping ->
                            rpBtn.updateIconTint(Theming.resolveThemeColor(resources))
                        else -> rpBtn.updateIconTint(widgetsColorDisabled)
                    }
                }
            }
        }
    }

    private fun loadNpCover(selectedSong: Music) {
        mAlbumIdNp = selectedSong.albumId
        mAlbumIdNp?.waitForCover(requireContext()) { bmp, error ->
            _npCoverBinding?.npCover?.loadWithError(bmp, error, R.drawable.ic_music_note_cover)
        }
    }

    private fun setRepeat() {
        with(mMediaPlayerHolder) {
            repeat(updatePlaybackStatus = isPlaying)
            updateRepeatStatus(onPlaybackCompletion = false)
        }
    }

    private fun saveSongPosition() {
        with(mMediaPlayerHolder) {
            val song = currentSong
            when (val position = playerPosition) {
                0 -> _npCoverBinding?.npLove?.callOnClick()
                else -> {
                    Lists.addToFavorites(requireActivity(), song, canRemove = false, position, launchedBy)
                    mUIControlInterface.onFavoriteAddedOrRemoved()
                }
            }
        }
    }

    private fun skip(isNext: Boolean) {
        with(mMediaPlayerHolder) {
            if (!isPlay) isPlay = true
            if (isSongFromPrefs) isSongFromPrefs = false
            if (isNext) {
                skip(isNext = true)
                return
            }
            instantReset()
        }
    }

    fun updateNpFavoritesIcon() {
        _npCoverBinding?.run {
            val favoriteIcon = Theming.getFavoriteIcon(isNotification = false)
            npLove.setImageResource(favoriteIcon)
            if (favoriteIcon == R.drawable.ic_favorite) {
                npLove.updateIconTint(Theming.resolveThemeColor(resources))
                return
            }
            npLove.updateIconTint(Theming.resolveWidgetsColorNormal(requireContext()))
        }
    }

    fun updateNpInfo() {
        if (::mMediaControlInterface.isInitialized) {
            with(mMediaPlayerHolder) {
                (currentSongFM ?: currentSong)?.let { song ->
                    // load album cover
                    if (mAlbumIdNp != song.albumId && mGoPreferences.isCovers) {
                        loadNpCover(song)
                    }
                    // load album/song info
                    var songTitle = song.title
                    if (mGoPreferences.songsVisualization == GoConstants.FN) {
                        songTitle = song.displayName.toFilenameWithoutExtension()
                    }
                    _nowPlayingBinding?.npSong?.text = songTitle
                    _nowPlayingBinding?.npArtistAlbum?.text =
                        getString(
                            R.string.artist_and_album,
                            song.artist,
                            song.album
                        )

                    // load song's duration
                    val selectedSongDuration = song.duration
                    _nowPlayingBinding?.npDuration?.text =
                        selectedSongDuration.toFormattedDuration(isAlbum = false, isSeekBar = true)
                    _nowPlayingBinding?.npSeekBar?.max = song.duration.toInt()

                    song.id?.toContentUri()?.toBitrate(requireContext())?.let { (first, second) ->
                        _nowPlayingBinding?.npRates?.text =
                            getString(R.string.rates, first, second)
                    }
                    updateNpFavoritesIcon()
                    updatePlayingStatus()
                }
            }
        }
    }

    fun updateProgress(position: Int) {
        _nowPlayingBinding?.npSeekBar?.progress = position
    }

    fun updatePlayingStatus() {
        with(mMediaPlayerHolder) {
            if (isPlaying) {
                _npControlsBinding?.npPlay?.setImageResource(R.drawable.ic_pause)
                return
            }
            _npControlsBinding?.npPlay?.setImageResource(R.drawable.ic_play)
        }
    }

    companion object {

        const val TAG_MODAL = "NP_BOTTOM_SHEET"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ModalSheet.
         */
        @JvmStatic
        fun newInstance() = NowPlaying()
    }
}
