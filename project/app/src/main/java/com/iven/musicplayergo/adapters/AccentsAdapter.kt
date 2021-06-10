package com.iven.musicplayergo.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.iven.musicplayergo.R
import com.iven.musicplayergo.goPreferences
import com.iven.musicplayergo.helpers.ThemeHelper

class AccentsAdapter(private val activity: Activity) :
    RecyclerView.Adapter<AccentsAdapter.AccentsHolder>() {

    private val mAccents = ThemeHelper.accents
    private var mSelectedAccent = goPreferences.accent
    private var mSelectedPosition = RecyclerView.NO_POSITION

    fun applyTheming() {
        goPreferences.accent = mSelectedAccent
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccentsHolder {
        return AccentsHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.accent_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount() = mAccents.size

    override fun onBindViewHolder(holder: AccentsHolder, position: Int) {
        holder.bindItems(mAccents[holder.absoluteAdapterPosition].first)
    }

    inner class AccentsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindItems(color: Int) {

            itemView.run {

                val circle = findViewById<ImageButton>(R.id.circle)
                val accent = ContextCompat.getColor(activity, color)
                val accentName = ThemeHelper.getAccentNameForPref(activity, mAccents[absoluteAdapterPosition].first)
                ThemeHelper.updateIconTint(circle, accent)

                contentDescription = accentName

                val cardView = itemView as MaterialCardView
                val colorText = itemView.findViewById<TextView>(R.id.color)

                cardView.strokeColor = accent

                if (color == mSelectedAccent) {
                    mSelectedPosition = absoluteAdapterPosition
                    cardView.strokeWidth = resources.getDimensionPixelSize(R.dimen.album_stroke)
                    colorText.setTextColor(accent)
                } else {
                    cardView.strokeWidth = 0
                    colorText.setTextColor(ThemeHelper.resolveColorAttr(
                        activity,
                        android.R.attr.colorButtonNormal
                    ))
                }

                colorText.text = accentName

                setOnClickListener {
                    if (mAccents[absoluteAdapterPosition].first != mSelectedAccent) {
                        notifyItemChanged(mSelectedPosition)
                        mSelectedAccent = mAccents[absoluteAdapterPosition].first
                        notifyItemChanged(absoluteAdapterPosition)
                    }
                }
            }
        }
    }
}
