package com.iven.musicplayergo.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.recyclical.datasource.DataSource
import com.iven.musicplayergo.R
import com.iven.musicplayergo.goPreferences
import java.util.*

object Utils {

    @JvmStatic
    fun addToHiddenItems(item: String) {
        val hiddenArtistsFolders = goPreferences.hiddenItems?.toMutableList()
        hiddenArtistsFolders?.add(item)
        goPreferences.hiddenItems = hiddenArtistsFolders?.toSet()
    }

    @JvmStatic
    fun makeHideItemDialog(
        context: Context,
        item: Pair<Int, String>,
        stringsList: MutableList<String>,
        dataSource: DataSource<Any>
    ): MaterialDialog {

        return MaterialDialog(context).show {

            cornerRadius(res = R.dimen.md_corner_radius)
            title(text = item.second)
            message(text = context.getString(R.string.hidden_items_pref_message, item.second))
            positiveButton {

                stringsList.remove(item.second)
                dataSource.set(stringsList)
                addToHiddenItems(item.second)
            }
            negativeButton {}
        }
    }

    @JvmStatic
    fun removeCheckableItems(newCheckableItems: Set<String>) {
        goPreferences.hiddenItems = newCheckableItems
    }

    @JvmStatic
    fun makeToast(context: Context, message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_LONG)
            .show()
    }

    @JvmStatic
    fun setupSearchViewForStringLists(
        searchView: SearchView,
        list: List<String>,
        onResultsChanged: (List<String>) -> Unit
    ) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override
            fun onQueryTextChange(newText: String): Boolean {
                onResultsChanged(
                    processQueryForStringsLists(
                        newText,
                        list
                    )
                )
                return false
            }

            override
            fun onQueryTextSubmit(query: String): Boolean {
                return false
            }
        })
    }

    @JvmStatic
    @SuppressLint("DefaultLocale")
    private fun processQueryForStringsLists(
        query: String,
        list: List<String>
    ): List<String> {
        // in real app you'd have it instantiated just once
        val results = mutableListOf<String>()

        try {
            // case insensitive search
            list.iterator().forEach {
                if (it.toLowerCase().startsWith(query.toLowerCase())) {
                    results.add(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results.toList()
    }

    @JvmStatic
    fun getSortedList(
        id: Int,
        list: MutableList<String>,
        defaultList: MutableList<String>
    ): MutableList<String> {
        return when (id) {

            R.id.ascending_sorting -> {

                Collections.sort(list, String.CASE_INSENSITIVE_ORDER)
                list
            }

            R.id.descending_sorting -> {

                Collections.sort(list, String.CASE_INSENSITIVE_ORDER)
                list.asReversed()
            }
            else -> defaultList
        }
    }
}
