package com.iven.musicplayergo.ui


interface UIControlInterface {
    fun onAppearanceChanged(isThemeChanged: Boolean)
    fun onOpenNewDetailsFragment()
    fun onArtistOrFolderSelected(artistOrFolder: String, launchedBy: String)
    fun onLovedSongsUpdate(clear: Boolean)
    fun onCloseActivity()
    fun onAddToFilter(stringToFilter: String?)
    fun onSongVisualizationChanged()
    fun onDenyPermission()
}
