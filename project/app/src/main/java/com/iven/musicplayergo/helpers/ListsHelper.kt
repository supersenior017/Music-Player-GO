package com.iven.musicplayergo.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import com.iven.musicplayergo.R
import com.iven.musicplayergo.enums.LaunchedBy
import com.iven.musicplayergo.enums.SortingOptions
import com.iven.musicplayergo.extensions.toFormattedDuration
import com.iven.musicplayergo.extensions.toSavedMusic
import com.iven.musicplayergo.extensions.toToast
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.models.Music
import java.util.*

@SuppressLint("DefaultLocale")
object ListsHelper {
    @JvmStatic
    fun processQueryForStringsLists(
            query: String?,
            list: List<String>?
    ): List<String>? {
        // In real app you'd have it instantiated just once
        val filteredStrings = mutableListOf<String>()

        return try {
            // Case insensitive search
            list?.iterator()?.forEach { filteredString ->
                if (filteredString.toLowerCase().contains(query?.toLowerCase()!!)) {
                    filteredStrings.add(filteredString)
                }
            }
            return filteredStrings
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun processQueryForMusic(query: String?, musicList: List<Music>?): List<Music>? {
        // In real app you'd have it instantiated just once
        val filteredSongs = mutableListOf<Music>()

        return try {
            // Case insensitive search
            musicList?.iterator()?.forEach { filteredSong ->
                if (filteredSong.title?.toLowerCase()!!.contains(query?.toLowerCase()!!)) {
                    filteredSongs.add(filteredSong)
                }
            }
            return filteredSongs
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getSortedList(
            id: SortingOptions,
            list: MutableList<String>?
    ) = when (id) {
        SortingOptions.DESCENDING_SORTING -> {
            list?.apply {
                Collections.sort(this, String.CASE_INSENSITIVE_ORDER)
            }
            list
        }

        SortingOptions.ASCENDING_SORTING -> {
            list?.apply {
                Collections.sort(this, String.CASE_INSENSITIVE_ORDER)
            }
            list?.asReversed()
        }
        else -> list
    }

    @JvmStatic
    fun getSortedListWithNull(
            id: SortingOptions,
            list: MutableList<String?>?
    ): MutableList<String>? {
        val withoutNulls = list?.map {
            transformNullToEmpty(it)
        }?.toMutableList()

        return getSortedList(id, withoutNulls)
    }

    private fun transformNullToEmpty(toTrans: String?): String {
        if (toTrans == null) {
            return ""
        }
        return toTrans
    }

    fun getSelectedSorting(sorting: SortingOptions, menu: Menu): MenuItem = when (sorting) {
        SortingOptions.DEFAULT_SORTING -> menu.findItem(R.id.default_sorting)
        SortingOptions.ASCENDING_SORTING -> menu.findItem(R.id.ascending_sorting)
        else -> menu.findItem(R.id.descending_sorting)
    }

    @JvmStatic
    fun getSortedMusicList(
            id: SortingOptions,
            list: MutableList<Music>?
    ) = when (id) {

        SortingOptions.DESCENDING_SORTING -> {
            list?.sortBy { it.title }
            list
        }

        SortingOptions.ASCENDING_SORTING -> {
            list?.sortBy { it.title }
            list?.asReversed()
        }

        SortingOptions.TRACK_SORTING -> {
            list?.sortBy { it.track }
            list
        }

        SortingOptions.TRACK_SORTING_INVERTED -> {
            list?.sortBy { it.track }
            list?.asReversed()
        }
        else -> list
    }

    @JvmStatic
    fun getSongsSorting(currentSorting: SortingOptions) = when (currentSorting) {
        SortingOptions.TRACK_SORTING -> SortingOptions.TRACK_SORTING_INVERTED
        SortingOptions.TRACK_SORTING_INVERTED -> SortingOptions.ASCENDING_SORTING
        SortingOptions.ASCENDING_SORTING -> SortingOptions.DESCENDING_SORTING
        else -> SortingOptions.TRACK_SORTING
    }

    @JvmStatic
    fun addToHiddenItems(item: String) {
        val hiddenArtistsFolders = goPreferences.filters?.toMutableList()
        hiddenArtistsFolders?.add(item)
        goPreferences.filters = hiddenArtistsFolders?.toSet()
    }

    @JvmStatic
    fun addToLovedSongs(
            context: Context,
            song: Music?,
            playerPosition: Int,
            launchedBy: LaunchedBy
    ) {
        val lovedSongs =
                if (goPreferences.lovedSongs != null) goPreferences.lovedSongs else mutableListOf()

        val songToSave = song?.toSavedMusic(playerPosition, launchedBy)

        songToSave?.let { savedSong ->
            if (!lovedSongs?.contains(savedSong)!!) {
                lovedSongs.add(
                        savedSong
                )
                context.getString(
                        R.string.loved_song_added,
                        savedSong.title,
                        savedSong.startFrom.toLong().toFormattedDuration(
                                isAlbum = false,
                                isSeekBar = false
                        )
                ).toToast(context)
                goPreferences.lovedSongs = lovedSongs
            }
        }
    }
}
