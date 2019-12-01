package com.iven.musicplayergo.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.recyclical.datasource.DataSource
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.iven.musicplayergo.R
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.musicLibrary
import com.iven.musicplayergo.ui.GenericViewHolder
import com.iven.musicplayergo.ui.ThemeHelper
import com.iven.musicplayergo.ui.UIControlInterface
import com.iven.musicplayergo.ui.Utils
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import kotlinx.android.synthetic.main.fragment_folders.*
import kotlinx.android.synthetic.main.search_toolbar.*
import java.io.File


/**
 * A simple [Fragment] subclass.
 * Use the [FoldersFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FoldersFragment : Fragment() {

    //views
    private lateinit var mFoldersRecyclerView: RecyclerView

    private lateinit var mSearchToolbar: Toolbar

    //tab_indicator fast scroller by reddit
    private lateinit var mIndicatorFastScrollerView: FastScrollerView
    private lateinit var mIndicatorFastScrollThumb: FastScrollerThumbView

    private lateinit var mFolders: MutableList<String>
    private var mFilteredFolders: List<String>? = null

    private lateinit var mDataSource: DataSource<Any>

    private lateinit var mUIControlInterface: UIControlInterface

    private lateinit var mSortMenuItem: MenuItem
    private var mSorting = R.id.ascending_sorting

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mUIControlInterface = activity as UIControlInterface
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (context != null) {

            mSearchToolbar = search_toolbar
            mSearchToolbar.inflateMenu(R.menu.menu_search)
            mSearchToolbar.overflowIcon =
                AppCompatResources.getDrawable(context!!, R.drawable.ic_sort)

            mSearchToolbar.title = getString(R.string.folders)

            mSearchToolbar.setNavigationOnClickListener {
                mUIControlInterface.onCloseActivity()
            }

            val menu = mSearchToolbar.menu

            mSorting = goPreferences.artistsSorting
            mSortMenuItem = menu.findItem(mSorting)

            mSortMenuItem.setTitleColor(ThemeHelper.resolveThemeAccent(context!!))

            setMenuOnItemClickListener(menu)

            mFoldersRecyclerView = folders_rv

            setupFilteredFolders()

            mDataSource = dataSourceOf(mFolders)

            // setup{} is an extension method on RecyclerView
            mFoldersRecyclerView.setup {
                withDataSource(mDataSource)
                withItem<String, GenericViewHolder>(R.layout.folder_item) {
                    onBind(::GenericViewHolder) { _, item ->

                        // GenericViewHolder is `this` here
                        title.text = item
                        subtitle.text = getParentFolder(item)
                    }

                    onClick {
                        // item is a `val` in `this` here
                        if (::mUIControlInterface.isInitialized)
                            mUIControlInterface.onArtistOrFolderSelected(item, true)
                    }
                }
            }

            mFoldersRecyclerView.addItemDecoration(
                ThemeHelper.getRecyclerViewDivider(
                    context!!
                )
            )

            setupIndicatorFastScrollerView()

            val itemSearch = mSearchToolbar.menu.findItem(R.id.action_search)
            val searchView = itemSearch.actionView as SearchView
            Utils.setupSearchViewForStringLists(
                searchView,
                mFolders,
                onResultsChanged = { newResults ->
                    mFilteredFolders = if (newResults.isEmpty()) {
                        null
                    } else {
                        newResults
                    }
                    mDataSource.set(mFilteredFolders ?: mFolders)
                })

        }
    }

    //getting parent path of the first song
    private fun getParentFolder(item: String): String {
        val songRootPath =
            musicLibrary.allSongsForFolder.getValue(item)[0].path
        return File(songRootPath!!).parentFile?.parent.toString()
    }

    @SuppressLint("DefaultLocale")
    private fun setupIndicatorFastScrollerView() {

        mIndicatorFastScrollerView = fastscroller
        mIndicatorFastScrollThumb = fastscroller_thumb

        if (goPreferences.foldersSorting == R.id.default_sorting) mIndicatorFastScrollerView.visibility =
            View.GONE

        //set indexes if artists rv is scrollable
        mFoldersRecyclerView.afterMeasured {
            if (mFoldersRecyclerView.computeVerticalScrollRange() > height) {

                mIndicatorFastScrollerView.setupWithRecyclerView(
                    mFoldersRecyclerView,
                    { position ->
                        val item =
                            (mFilteredFolders ?: mFolders)[position] // Get your model object
                        // or fetch the section at [position] from your database

                        FastScrollItemIndicator.Text(
                            item.substring(
                                0,
                                1
                            ).toUpperCase() // Grab the first letter and capitalize it
                        ) // Return a text tab_indicator
                    }
                )

                mIndicatorFastScrollThumb.setupWithFastScroller(mIndicatorFastScrollerView)

                mIndicatorFastScrollerView.useDefaultScroller = false
                mIndicatorFastScrollerView.itemIndicatorSelectedCallbacks += object :
                    FastScrollerView.ItemIndicatorSelectedCallback {
                    override fun onItemIndicatorSelected(
                        indicator: FastScrollItemIndicator,
                        indicatorCenterY: Int,
                        itemPosition: Int
                    ) {
                        val artistsLayoutManager =
                            mFoldersRecyclerView.layoutManager as LinearLayoutManager
                        artistsLayoutManager.scrollToPositionWithOffset(itemPosition, 0)
                    }
                }
            }
        }
    }

    private fun setMenuOnItemClickListener(menu: Menu) {
        mSearchToolbar.setOnMenuItemClickListener {

            mFolders = Utils.getSortedList(
                it.itemId,
                mFolders,
                musicLibrary.allSongsForFolder.keys.toMutableList()
            )

            mIndicatorFastScrollerView.visibility =
                if (it.itemId == R.id.default_sorting) View.GONE else View.VISIBLE

            mDataSource.set(mFolders)

            mSortMenuItem.setTitleColor(
                ThemeHelper.resolveColorAttr(
                    context!!,
                    android.R.attr.textColorPrimary
                )
            )

            mSorting = it.itemId

            mSortMenuItem = menu.findItem(mSorting)

            mSortMenuItem.setTitleColor(ThemeHelper.resolveThemeAccent(context!!))

            goPreferences.foldersSorting = it.itemId

            return@setOnMenuItemClickListener true
        }
    }

    private fun setupFilteredFolders() {
        mFolders = Utils.getSortedList(
            goPreferences.foldersSorting,
            musicLibrary.allSongsForFolder.keys.toMutableList(),
            musicLibrary.allSongsForFolder.keys.toMutableList()
        )
    }

    private fun MenuItem.setTitleColor(color: Int) {
        val hexColor = Integer.toHexString(color).substring(2)
        val html = "<font color='#$hexColor'>$title</font>"
        this.title = ThemeHelper.buildSpanned(html)
    }

    //viewTreeObserver extension to measure layout params
    //https://antonioleiva.com/kotlin-ongloballayoutlistener/
    private inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    f()
                }
            }
        })
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment NowPlaying.
         */
        @JvmStatic
        fun newInstance() = FoldersFragment()
    }
}
