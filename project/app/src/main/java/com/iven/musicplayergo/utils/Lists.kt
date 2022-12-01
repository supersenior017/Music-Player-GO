package com.iven.musicplayergo.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.extensions.findSorting
import com.iven.musicplayergo.extensions.toFormattedDuration
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.models.Sorting
import com.iven.musicplayergo.ui.UIControlInterface
import java.util.*

@SuppressLint("DefaultLocale")
object Lists {

    @JvmStatic
    fun processQueryForStringsLists(query: String?, list: List<String>?): List<String>? {
        // In real app you'd have it instantiated just once
        val filteredStrings = mutableListOf<String>()

        return try {
            // Case insensitive search
            list?.iterator()?.let { iterate ->
                while (iterate.hasNext()) {
                    val filteredString = iterate.next()
                    if (filteredString.lowercase().contains(query?.lowercase()!!)) {
                        filteredStrings.add(filteredString)
                    }
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
        val isShowDisplayName =
            GoPreferences.getPrefsInstance().songsVisualization== GoConstants.FN
        return try {
            // Case insensitive search
            musicList?.iterator()?.let { iterate ->
                while (iterate.hasNext()) {
                    val filteredSong = iterate.next()
                    val toFilter = if (isShowDisplayName) {
                        filteredSong.displayName
                    } else {
                        filteredSong.title
                    }
                    if (toFilter?.lowercase()!!.contains(query?.lowercase()!!)) {
                        filteredSongs.add(filteredSong)
                    }
                }
            }
            return filteredSongs
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getSortedList(id: Int, list: MutableList<String>?) = when (id) {
        GoConstants.ASCENDING_SORTING -> list?.apply {
            Collections.sort(this, String.CASE_INSENSITIVE_ORDER)
        }
        GoConstants.DESCENDING_SORTING -> list?.apply {
            Collections.sort(this, String.CASE_INSENSITIVE_ORDER)
        }?.asReversed()
        else -> list
    }

    @JvmStatic
    fun getSortedListWithNull(id: Int, list: MutableList<String?>?): MutableList<String>? {
        val withoutNulls = list?.map {
            transformNullToEmpty(it)
        }?.toMutableList()
        return getSortedList(id, withoutNulls)
    }

    private fun transformNullToEmpty(toTrans: String?): String {
        if (toTrans == null) return ""
        return toTrans
    }

    @JvmStatic
    fun getSelectedSorting(sorting: Int, menu: Menu): MenuItem {
        return when (sorting) {
            GoConstants.ASCENDING_SORTING -> menu.findItem(R.id.ascending_sorting)
            GoConstants.DESCENDING_SORTING -> menu.findItem(R.id.descending_sorting)
            else -> menu.findItem(R.id.default_sorting)
        }
    }

    @JvmStatic
    fun getSelectedSortingForMusic(sorting: Int, menu: Menu): MenuItem {
        return when (sorting) {
            GoConstants.ASCENDING_SORTING -> menu.findItem(R.id.ascending_sorting)
            GoConstants.DESCENDING_SORTING -> menu.findItem(R.id.descending_sorting)
            GoConstants.DATE_ADDED_SORTING -> menu.findItem(R.id.date_added_sorting)
            GoConstants.DATE_ADDED_SORTING_INV -> menu.findItem(R.id.date_added_sorting_inv)
            GoConstants.ARTIST_SORTING -> menu.findItem(R.id.artist_sorting)
            GoConstants.ARTIST_SORTING_INV -> menu.findItem(R.id.artist_sorting_inv)
            GoConstants.ALBUM_SORTING -> menu.findItem(R.id.album_sorting)
            GoConstants.ALBUM_SORTING_INV -> menu.findItem(R.id.album_sorting_inv)
            else -> menu.findItem(R.id.default_sorting)
        }
    }

    @JvmStatic
    fun getSortedMusicList(id: Int, list: List<Music>?): List<Music>? {
        return when (id) {
            GoConstants.ASCENDING_SORTING -> getSortedListBySelectedVisualization(list)
            GoConstants.DESCENDING_SORTING -> getSortedListBySelectedVisualization(list)?.asReversed()
            GoConstants.TRACK_SORTING -> list?.sortedBy { it.track }
            GoConstants.TRACK_SORTING_INVERTED -> list?.sortedBy { it.track }?.asReversed()
            else -> list
        }
    }

    @JvmStatic
    fun getSortedMusicListForFolder(id: Int, list: List<Music>?): List<Music>? {
        return when (id) {
            GoConstants.ASCENDING_SORTING -> list?.sortedBy { it.displayName }
            GoConstants.DESCENDING_SORTING -> list?.sortedBy { it.displayName }?.asReversed()
            GoConstants.DATE_ADDED_SORTING -> list?.sortedBy { it.dateAdded }?.asReversed()
            GoConstants.DATE_ADDED_SORTING_INV -> list?.sortedBy { it.dateAdded }
            GoConstants.ARTIST_SORTING -> list?.sortedBy { it.artist }
            GoConstants.ARTIST_SORTING_INV -> list?.sortedBy { it.artist }?.asReversed()
            else -> list
        }
    }

    @JvmStatic
    fun getSortedMusicListForAllMusic(id: Int, list: List<Music>?): List<Music>? {
        return when (id) {
            GoConstants.ASCENDING_SORTING -> getSortedListBySelectedVisualization(list)
            GoConstants.DESCENDING_SORTING -> getSortedListBySelectedVisualization(list)?.asReversed()
            GoConstants.TRACK_SORTING -> list?.sortedBy { it.track }
            GoConstants.TRACK_SORTING_INVERTED -> list?.sortedBy { it.track }?.asReversed()
            GoConstants.DATE_ADDED_SORTING -> list?.sortedBy { it.dateAdded }?.asReversed()
            GoConstants.DATE_ADDED_SORTING_INV -> list?.sortedBy { it.dateAdded }
            GoConstants.ARTIST_SORTING -> list?.sortedBy { it.artist }
            GoConstants.ARTIST_SORTING_INV -> list?.sortedBy { it.artist }?.asReversed()
            GoConstants.ALBUM_SORTING -> list?.sortedBy { it.album }
            GoConstants.ALBUM_SORTING_INV -> list?.sortedBy { it.album }?.asReversed()
            else -> list
        }
    }

    private fun getSortedListBySelectedVisualization(list: List<Music>?) = list?.sortedBy {
        if (GoPreferences.getPrefsInstance().songsVisualization == GoConstants.FN) {
            it.displayName
        } else {
            it.title
        }
    }

    @JvmStatic
    fun getSongsSorting(currentSorting: Int) = when (currentSorting) {
        GoConstants.TRACK_SORTING -> GoConstants.TRACK_SORTING_INVERTED
        GoConstants.TRACK_SORTING_INVERTED -> GoConstants.ASCENDING_SORTING
        GoConstants.ASCENDING_SORTING -> GoConstants.DESCENDING_SORTING
        else -> GoConstants.TRACK_SORTING
    }

    @JvmStatic
    fun getSongsDisplayNameSorting(currentSorting: Int): Int {
        if (currentSorting == GoConstants.ASCENDING_SORTING) {
            return GoConstants.DESCENDING_SORTING
        }
        return GoConstants.ASCENDING_SORTING
    }

    fun hideItems(items: List<String>) {
        val hiddenArtistsFolders = GoPreferences.getPrefsInstance().filters?.toMutableList()
        hiddenArtistsFolders?.addAll(items)
        GoPreferences.getPrefsInstance().filters = hiddenArtistsFolders?.toSet()
    }

    @JvmStatic
    fun addToFavorites(
        context: Context,
        song: Music?,
        canRemove: Boolean,
        playerPosition: Int,
        launchedBy: String
    ) {
        val favorites = GoPreferences.getPrefsInstance().favorites?.toMutableList() ?: mutableListOf()
        song?.copy(startFrom = playerPosition, launchedBy = launchedBy)?.let { savedSong ->
            if (!favorites.contains(savedSong)) {
                favorites.add(savedSong)

                var msg = context.getString(
                    R.string.favorite_added,
                    savedSong.title,
                    playerPosition.toLong().toFormattedDuration(
                        isAlbum = false,
                        isSeekBar = false
                    )
                )
                if (playerPosition == 0) {
                    msg = msg.replace(context.getString(R.string.favorites_no_position), "")
                }

                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else if (canRemove) {
                favorites.remove(savedSong)
            }
            GoPreferences.getPrefsInstance().favorites = favorites
        }
    }

    @JvmStatic
    fun getDefSortingMode() = if (GoPreferences.getPrefsInstance().songsVisualization == GoConstants.FN) {
        GoConstants.ASCENDING_SORTING
    } else {
        GoConstants.TRACK_SORTING
    }

    @JvmStatic
    fun getUserSorting(launchedBy: String) = GoPreferences.PREFS_DETAILS_SORTING.findSorting(launchedBy)

    @JvmStatic
    fun addToSortings(
        activity: Activity,
        artistOrFolder: String?,
        launchedBy: String,
        sorting: Int
    ) {
        val prefs = GoPreferences.getPrefsInstance()
        val toSorting = Sorting(artistOrFolder, launchedBy, prefs.songsVisualization, sorting)
        val sortings = prefs.sortings?.toMutableList() ?: mutableListOf()
        artistOrFolder?.findSorting(launchedBy)?.let { toReplaceSorting ->
            sortings.remove(toReplaceSorting)
            val newSorting = toReplaceSorting.copy(sorting = sorting)
            sortings.add(newSorting)
            prefs.sortings = sortings
            return
        }
        if (!sortings.contains(toSorting)) {
            sortings.add(toSorting)
        }
        prefs.sortings = sortings
        (activity as UIControlInterface).onUpdateSortings()
    }
}
