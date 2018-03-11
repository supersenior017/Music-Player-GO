package com.iven.musicplayergo.models;

import android.support.annotation.NonNull;

import java.util.ArrayList;

public class Album {

    public final ArrayList<Song> songs;
    public int position;

    public Album() {
        this.songs = new ArrayList<>();
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getTitle() {
        return getFirstSong().albumName;
    }

    public int getArtistId() {
        return getFirstSong().artistId;
    }

    public String getArtistName() {
        return getFirstSong().artistName;
    }

    public int getYear() {
        return getFirstSong().year;
    }

    @NonNull
    private Song getFirstSong() {
        return songs.isEmpty() ? Song.EMPTY_SONG : songs.get(0);
    }
}
