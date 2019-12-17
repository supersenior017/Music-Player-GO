package com.iven.musicplayergo.music

import android.annotation.SuppressLint
import android.content.Context
import java.io.File


class MusicLibrary {

    lateinit var allSongsFiltered: MutableList<Music>

    //keys: artist || value: its songs
    lateinit var allSongsByArtist: Map<String?, List<Music>>

    //keys: artist || value: albums
    val allAlbumsByArtist = hashMapOf<String, List<Album>>()

    //keys: artist || value: songs contained in the folder
    lateinit var allSongsByFolder: Map<String, List<Music>>

    val randomMusic get() = allSongsFiltered.random()

    // Extension method to sort the device music
    @Suppress("DEPRECATION")
    @SuppressLint("InlinedApi")
    fun buildLibrary(
        context: Context,
        loadedSongs: MutableList<Music>
    ): HashMap<String, List<Album>> {


        // Removing duplicates by comparing everything except path which is different
        // if the same song is hold in different paths
        allSongsFiltered =
            loadedSongs.distinctBy { it.artist to it.year to it.track to it.title to it.duration to it.album to it.albumId }
                .toMutableList()

        allSongsByArtist = allSongsFiltered.groupBy { it.artist }

        allSongsByArtist.keys.iterator().forEach {

            allAlbumsByArtist[it!!] = MusicUtils.buildSortedArtistAlbums(
                context.resources,
                allSongsByArtist.getValue(it)
            )
        }

        allSongsByFolder = allSongsFiltered.groupBy {
            val file = File(it.path!!).parentFile
            file!!.name
        }

        return allAlbumsByArtist
    }
}
