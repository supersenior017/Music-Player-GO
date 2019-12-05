package com.iven.musicplayergo.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
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
import kotlinx.android.synthetic.main.fragment_artists.*
import kotlinx.android.synthetic.main.search_toolbar.*


/**
 * A simple [Fragment] subclass.
 * Use the [ArtistsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ArtistsFragment : Fragment() {

    //views
    private lateinit var mArtistsRecyclerView: RecyclerView

    private lateinit var mSearchToolbar: Toolbar

    //tab_indicator fast scroller by reddit
    private lateinit var mIndicatorFastScrollerView: FastScrollerView
    private lateinit var mIndicatorFastScrollThumb: FastScrollerThumbView

    private lateinit var mArtists: MutableList<String>
    private var mFilteredArtists: List<String>? = null

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
        return inflater.inflate(R.layout.fragment_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mSorting = goPreferences.artistsSorting

        mArtists = Utils.getSortedList(
            mSorting,
            musicLibrary.allAlbumsForArtist.keys.toMutableList(),
            musicLibrary.allAlbumsForArtist.keys.toMutableList()
        )

        context?.let {

            mSearchToolbar = search_toolbar

            mSearchToolbar.apply {

                inflateMenu(R.menu.menu_search)

                overflowIcon =
                    AppCompatResources.getDrawable(it, R.drawable.ic_sort)

                title = getString(R.string.artists)

                setNavigationOnClickListener {
                    mUIControlInterface.onCloseActivity()
                }

                menu.apply {
                    mSortMenuItem = findItem(mSorting)
                    mSortMenuItem.setTitleColor(ThemeHelper.resolveThemeAccent(it))

                    setMenuOnItemClickListener(it, this)

                    Utils.setupSearchViewForStringLists(
                        findItem(R.id.action_search).actionView as SearchView,
                        mArtists,
                        onResultsChanged = { newResults ->
                            mFilteredArtists = if (newResults.isEmpty()) {
                                null
                            } else {
                                newResults
                            }
                            mDataSource.set(mFilteredArtists ?: mArtists)
                        })
                }
            }

            mArtistsRecyclerView = artists_rv

            mDataSource = dataSourceOf(mArtists)

            mArtistsRecyclerView.apply {

                // setup{} is an extension method on RecyclerView
                setup {

                    // item is a `val` in `this` here
                    withDataSource(mDataSource)
                    withItem<String, GenericViewHolder>(R.layout.generic_item) {

                        onBind(::GenericViewHolder) { _, item ->
                            // GenericViewHolder is `this` here
                            title.text = item
                            subtitle.text = getArtistSubtitle(item)
                        }

                        onClick {
                            if (::mUIControlInterface.isInitialized)
                                mUIControlInterface.onArtistOrFolderSelected(item, false)
                        }
                    }

                    addItemDecoration(
                        ThemeHelper.getRecyclerViewDivider(it)
                    )
                }
            }

            setupIndicatorFastScrollerView()
        }
    }

    private fun getArtistSubtitle(item: String): String {
        return getString(
            R.string.artist_info,
            musicLibrary.allAlbumsForArtist.getValue(item).size,
            musicLibrary.allSongsForArtist.getValue(item).size
        )
    }

    @SuppressLint("DefaultLocale")
    private fun setupIndicatorFastScrollerView() {

        mIndicatorFastScrollerView = fastscroller

        if (mSorting == R.id.default_sorting) mIndicatorFastScrollerView.visibility =
            View.GONE

        mIndicatorFastScrollThumb = fastscroller_thumb

        //set indexes if artists rv is scrollable
        mArtistsRecyclerView.afterMeasured {
            if (mArtistsRecyclerView.computeVerticalScrollRange() > height) {

                mIndicatorFastScrollerView.setupWithRecyclerView(
                    mArtistsRecyclerView,
                    { position ->
                        val item =
                            (mFilteredArtists ?: mArtists)[position] // Get your model object
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
                            mArtistsRecyclerView.layoutManager as LinearLayoutManager
                        artistsLayoutManager.scrollToPositionWithOffset(itemPosition, 0)
                    }
                }
            }
        }
    }

    private fun setMenuOnItemClickListener(context: Context, menu: Menu) {
        mSearchToolbar.setOnMenuItemClickListener {

            mArtists = Utils.getSortedList(
                it.itemId,
                mArtists,
                musicLibrary.allAlbumsForArtist.keys.toMutableList()
            )

            mIndicatorFastScrollerView.visibility =
                if (it.itemId == R.id.default_sorting) View.GONE else View.VISIBLE

            mDataSource.set(mArtists)

            mSortMenuItem.setTitleColor(
                ThemeHelper.resolveColorAttr(
                    context,
                    android.R.attr.textColorPrimary
                )
            )

            mSorting = it.itemId

            mSortMenuItem = menu.findItem(mSorting)

            mSortMenuItem.setTitleColor(ThemeHelper.resolveThemeAccent(context))

            goPreferences.artistsSorting = mSorting

            return@setOnMenuItemClickListener true
        }
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
         * @return A new instance of fragment MusicFragment.
         */
        @JvmStatic
        fun newInstance() = ArtistsFragment()
    }
}
