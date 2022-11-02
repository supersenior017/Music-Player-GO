package com.iven.musicplayergo.preferences

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.databinding.FilterItemBinding
import com.iven.musicplayergo.utils.Theming


class FiltersAdapter: RecyclerView.Adapter<FiltersAdapter.CheckableItemsHolder>() {

    private val mItemsToRemove = mutableListOf<String>()

    private val mAvailableItems = GoPreferences.getPrefsInstance().filters?.sorted()?.toMutableList()

    fun getUpdatedItems() = mAvailableItems?.apply {
        removeAll(mItemsToRemove.toSet())
    }?.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckableItemsHolder {
        val binding = FilterItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CheckableItemsHolder(binding)
    }

    override fun getItemCount() = mAvailableItems?.size!!

    override fun onBindViewHolder(holder: CheckableItemsHolder, position: Int) {
        holder.bindItems(mAvailableItems?.get(position))
    }

    inner class CheckableItemsHolder(private val binding: FilterItemBinding): RecyclerView.ViewHolder(binding.root) {

        fun bindItems(itemFilter: String?) {

            with(binding.root) {
                text = itemFilter
                setOnCheckedChangeListener { _, b ->
                    if (b) {
                        setTextColor(Theming.resolveColorAttr(context, android.R.attr.textColorPrimary))
                        mItemsToRemove.remove(itemFilter)
                    } else {
                        setTextColor(Theming.resolveWidgetsColorNormal(context))
                        mItemsToRemove.add(itemFilter!!)
                    }
                }
            }
        }
    }
}
