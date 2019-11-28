package com.iven.musicplayergo

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.iven.musicplayergo.music.Music
import java.lang.reflect.Type


class MusicPlayerGoPreferences(context: Context) {

    private val prefsLastPlayedSong = context.getString(R.string.last_played_song_pref)
    private val prefsLovedSongs = context.getString(R.string.loved_songs_pref)

    private val prefsTheme = context.getString(R.string.theme_pref)
    private val prefsThemeDefault = context.getString(R.string.theme_pref_light)
    private val prefsAccent = context.getString(R.string.accent_pref)

    private val prefsTabs = context.getString(R.string.tabs_pref)

    private val prefsActiveFragments = context.getString(R.string.active_fragments_pref)
    private val prefsActiveFragmentsDefault =
        context.resources.getStringArray(R.array.activeFragmentsEntryArray).toMutableSet()

    private val prefsArtistsSorting = context.getString(R.string.artists_sorting_pref)
    private val prefsFoldersSorting = context.getString(R.string.folders_sorting_pref)

    private val prefsFocus = context.getString(R.string.focus_pref)
    private val prefsHeadsetPlug = context.getString(R.string.headset_pref)

    private val mPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val mGson = GsonBuilder().create()

    private val typeMusic: Type = object : TypeToken<Pair<Music, Int>>() {}.type
    private val typeLovedSong: Type = object : TypeToken<MutableList<Pair<Music, Int>>>() {}.type

    var lastPlayedSong: Pair<Music, Int>?
        get() = getObject(
            prefsLastPlayedSong,
            typeMusic
        )
        set(value) = putObject(prefsLastPlayedSong, value)

    var lovedSongs: MutableList<Pair<Music, Int>>?
        get() = getObject(
            prefsLovedSongs,
            typeLovedSong
        )
        set(value) = putObject(prefsLovedSongs, value)

    var theme: String?
        get() = mPrefs.getString(prefsTheme, prefsThemeDefault)
        set(value) = mPrefs.edit().putString(prefsTheme, value).apply()

    var accent: Int
        get() = mPrefs.getInt(prefsAccent, R.color.deep_purple)
        set(value) = mPrefs.edit().putInt(prefsAccent, value).apply()

    var isTabsEnabled: Boolean
        get() = mPrefs.getBoolean(prefsTabs, true)
        set(value) = mPrefs.edit().putBoolean(prefsTabs, value).apply()

    var activeFragments: Set<String>?
        get() = mPrefs.getStringSet(prefsActiveFragments, prefsActiveFragmentsDefault)
        set(value) = mPrefs.edit().putStringSet(prefsActiveFragments, value).apply()

    var artistsSorting: Int
        get() = mPrefs.getInt(prefsArtistsSorting, R.id.ascending_sorting)
        set(value) = mPrefs.edit().putInt(prefsArtistsSorting, value).apply()

    var foldersSorting: Int
        get() = mPrefs.getInt(prefsFoldersSorting, R.id.default_sorting)
        set(value) = mPrefs.edit().putInt(prefsFoldersSorting, value).apply()

    var isFocusEnabled: Boolean
        get() = mPrefs.getBoolean(prefsFocus, true)
        set(value) = mPrefs.edit().putBoolean(prefsFocus, value).apply()

    var isHeadsetPlugEnabled: Boolean
        get() = mPrefs.getBoolean(prefsHeadsetPlug, true)
        set(value) = mPrefs.edit().putBoolean(prefsHeadsetPlug, value).apply()

    /**
     * Saves object into the Preferences.
     * Only the fields are stored. Methods, Inner classes, Nested classes and inner interfaces are not stored.
     **/
    private fun <T> putObject(key: String, y: T) {
        //Convert object to JSON String.
        val inString = mGson.toJson(y)
        //Save that String in SharedPreferences
        mPrefs.edit().putString(key, inString).apply()
    }

    /**
     * Get object from the Preferences.
     **/
    private fun <T> getObject(key: String, t: Type): T? {
        //We read JSON String which was saved.
        val value = mPrefs.getString(key, null)

        //JSON String was found which means object can be read.
        //We convert this JSON String to model object. Parameter "c" (of type Class<T>" is used to cast.
        return mGson.fromJson(value, t)
    }
}

