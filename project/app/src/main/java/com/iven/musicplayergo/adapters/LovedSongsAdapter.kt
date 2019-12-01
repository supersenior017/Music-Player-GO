package com.iven.musicplayergo.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.iven.musicplayergo.R
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.music.Music
import com.iven.musicplayergo.music.MusicUtils
import com.iven.musicplayergo.musicLibrary
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.ui.ThemeHelper
import com.iven.musicplayergo.ui.UIControlInterface
import com.iven.musicplayergo.ui.Utils

const val UNKNOWN = "<unknown>"
const val UNKNOWN_REPLACE = "unknown"

class LovedSongsAdapter(
    private val context: Context,
    private val lovedSongsDialog: MaterialDialog,
    private val uiControlInterface: UIControlInterface,
    private val mediaPlayerHolder: MediaPlayerHolder
) :
    RecyclerView.Adapter<LovedSongsAdapter.LoveHolder>() {

    private var mLovedSongs = goPreferences.lovedSongs?.toMutableList()

    fun swapSongs(lovedSongs: MutableList<Pair<Music, Int>>) {
        mLovedSongs = lovedSongs
        notifyDataSetChanged()
        uiControlInterface.onLovedSongsUpdate()
        if (mLovedSongs!!.isEmpty()) lovedSongsDialog.dismiss()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoveHolder {
        return LoveHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.generic_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return mLovedSongs!!.size
    }

    override fun onBindViewHolder(holder: LoveHolder, position: Int) {
        holder.bindItems(mLovedSongs?.get(holder.adapterPosition)!!)
    }

    inner class LoveHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindItems(lovedSong: Pair<Music, Int>) {

            val title = itemView.findViewById<TextView>(R.id.title)
            val subtitle = itemView.findViewById<TextView>(R.id.subtitle)

            val song = lovedSong.first
            title.text = ThemeHelper.buildSpanned(
                context.getString(
                    R.string.loved_song_title,
                    song.title,
                    if (song.artist != UNKNOWN) song.artist else UNKNOWN_REPLACE
                )
            )
            subtitle.text = ThemeHelper.buildSpanned(
                context.getString(
                    R.string.loved_song_subtitle,
                    MusicUtils.formatSongDuration(lovedSong.second.toLong(), false),
                    MusicUtils.formatSongDuration(song.duration, false)
                )
            )

            itemView.setOnClickListener {
                mediaPlayerHolder.isSongFromLovedSongs = Pair(true, lovedSong.second)
                uiControlInterface.onSongSelected(
                    song,
                    MusicUtils.getAlbumFromList(
                        song.album,
                        musicLibrary.allAlbumsForArtist[song.artist]!!
                    )
                        .first
                        .music!!
                )
            }
            itemView.setOnLongClickListener {
                Utils.showDeleteLovedSongDialog(context, lovedSong, this@LovedSongsAdapter)
                return@setOnLongClickListener true
            }
        }
    }
}
