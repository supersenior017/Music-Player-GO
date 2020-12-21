package com.iven.musicplayergo.extensions

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.iven.musicplayergo.R
import com.iven.musicplayergo.helpers.ThemeHelper
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import kotlin.math.max


// viewTreeObserver extension to measure layout params
// https://antonioleiva.com/kotlin-ongloballayoutlistener/
inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (measuredWidth > 0 && measuredHeight > 0) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                f()
            }
        }
    })
}

@SuppressLint("DefaultLocale")
fun String.getFastScrollerItem(context: Context): FastScrollItemIndicator {
    var charAtZero = context.getString(R.string.fastscroller_dummy_item)
    if (isNotEmpty()) {
        charAtZero = "${get(0)}"
    }
    return FastScrollItemIndicator.Text(
        charAtZero.toUpperCase() // Grab the first letter and capitalize it
    )
}

// Extension to set menu items text color
fun MenuItem.setTitleColor(color: Int) {
    SpannableString(title).apply {
        setSpan(ForegroundColorSpan(color), 0, length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        title = this
    }
}

fun FragmentManager.addFragment(fragment: Fragment, tag: String?) {
    commit {
        addToBackStack(null)
        add(
            R.id.container,
            fragment,
            tag
        )
    }
}

fun FragmentManager.goBackFromFragment(isFragmentExpanded: Boolean) {
    if (isFragmentExpanded && backStackEntryCount >= 1) {
        popBackStack()
    }
}

fun FragmentManager.isFragment(fragmentTag: String): Boolean {
    val df = findFragmentByTag(fragmentTag)
    return df != null && df.isVisible && df.isAdded
}

fun View.createCircularReveal(isErrorFragment: Boolean, show: Boolean): Animator {

    val revealDuration: Long = if (isErrorFragment) {
        1500
    } else {
        500
    }
    val radius = max(width, height).toFloat()

    val startRadius = if (show) {
        0f
    } else {
        radius
    }
    val finalRadius = if (show) {
        radius
    } else {
        0f
    }

    val cx = if (isErrorFragment) {
        width / 2
    } else {
        0
    }
    val cy = if (isErrorFragment) {
        height / 2
    } else {
        0
    }
    val animator =
        ViewAnimationUtils.createCircularReveal(
            this,
            cx,
            cy,
            startRadius,
            finalRadius
        ).apply {
            interpolator = FastOutSlowInInterpolator()
            duration = revealDuration
            doOnEnd {
                if (!show) {
                    handleViewVisibility(false)
                }
            }
            start()
        }

    val windowBackground = ContextCompat.getColor(context, R.color.windowBackground)
    val closeColor = ThemeHelper.resolveColorAttr(context, R.attr.colorControlHighlight)
    val accent = if (!show) {
        windowBackground
    } else {
        ThemeHelper.resolveThemeAccent(context)
    }

    val startColor = if (isErrorFragment) {
        ContextCompat.getColor(context, R.color.red)
    } else {
        accent
    }
    val endColor = if (show) {
        windowBackground
    } else {
        closeColor
    }

    ValueAnimator().apply {
        setIntValues(startColor, endColor)
        setEvaluator(ArgbEvaluatorCompat())
        addUpdateListener { valueAnimator -> setBackgroundColor((valueAnimator.animatedValue as Int)) }
        duration = revealDuration
        if (isErrorFragment) {
            doOnEnd {
                background =
                    ThemeHelper.createColouredRipple(
                        context,
                        ContextCompat.getColor(context, R.color.red),
                        R.drawable.ripple
                    )
            }
        }
        start()
    }
    return animator
}

// https://stackoverflow.com/a/53986874
fun RecyclerView.smoothSnapToPosition(position: Int) {
    val smoothScroller = object : LinearSmoothScroller(this.context) {
        override fun getVerticalSnapPreference(): Int {
            return SNAP_TO_START
        }

        override fun getHorizontalSnapPreference(): Int {
            return SNAP_TO_START
        }

        override fun onStop() {
            super.onStop()
            findViewHolderForAdapterPosition(position)
                ?.itemView?.performClick()
        }
    }
    smoothScroller.targetPosition = position
    layoutManager?.startSmoothScroll(smoothScroller)
}

//add swipe features to a RecyclerView
fun RecyclerView.addBidirectionalSwipeHandler(
        context: Context,
        isDialog: Boolean,
        onSwiped: (
                viewHolder: RecyclerView.ViewHolder,
                direction: Int
        ) -> Unit
) {
    val swipeLeftCallback = instantiateSwipeHandler(context, isDialog, ItemTouchHelper.RIGHT, onSwiped)
    ItemTouchHelper(swipeLeftCallback).attachToRecyclerView(this)

    val swipeRightCallback = instantiateSwipeHandler(context, isDialog, ItemTouchHelper.LEFT, onSwiped)
    ItemTouchHelper(swipeRightCallback).attachToRecyclerView(this)
}

private fun instantiateSwipeHandler(
        context: Context,
        isDialog: Boolean,
        direction: Int,
        onSwiped: (
            viewHolder: RecyclerView.ViewHolder,
            direction: Int
        ) -> Unit
): ItemTouchHelper.SimpleCallback {
    return object : ItemTouchHelper.SimpleCallback(
        0,
        direction
    ) {

        val resources = context.resources
        val red = ContextCompat.getColor(context, R.color.red)

        val firstIcon = resources.getDrawable(R.drawable.ic_queue_add, null).apply {
            mutate().setTint(ContextCompat.getColor(context, R.color.green))
        }

        val firstIconAlt = resources.getDrawable(R.drawable.ic_delete, null).apply {
            mutate().setTint(red)
        }

        val secondIcon = if (isDialog) {
            resources.getDrawable(R.drawable.ic_delete, null)
        } else {
            resources.getDrawable(R.drawable.ic_favorite, null)
        }.apply {
            mutate().setTint(red)
        }

        var selectedIcon = firstIcon

        var firstColor = ColorDrawable(ContextCompat.getColor(context, R.color.swipeActionDeleteColor))

        var secondColor = if (isDialog) {
            firstColor
        } else {
            ColorDrawable(ContextCompat.getColor(context, R.color.swipeActionAddColor))
        }

        var background = secondColor

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(
            viewHolder: RecyclerView.ViewHolder,
            direction: Int
        ) {
            onSwiped(viewHolder, direction)
        }

        // Credits to Aidan Follestad :)
        // https://github.com/afollestad/recyclical/blob/master/swipe/src/main/java/com/afollestad/recyclical/swipe/SwipeItemTouchListener.kt#L120
        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            val itemView = viewHolder.itemView

            when {
                dX > 0 -> {
                    selectedIcon = if (isDialog) {
                        firstIconAlt
                    } else {
                        background = secondColor
                        firstIcon
                    }

                    val iconMargin = (itemView.height - selectedIcon.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - selectedIcon.intrinsicHeight) / 2
                    val iconBottom = iconTop + selectedIcon!!.intrinsicHeight
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = iconLeft + selectedIcon.intrinsicWidth

                    selectedIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    background.setBounds(
                            itemView.left,
                            itemView.top,
                            itemView.left + dX.toInt(),
                            itemView.bottom
                    )
                }
                dX < 0 -> {
                    selectedIcon = secondIcon
                    background = firstColor

                    val iconMargin = (itemView.height - selectedIcon.intrinsicHeight) / 2
                    val iconTop = itemView.top + (itemView.height - selectedIcon.intrinsicHeight) / 2
                    val iconBottom = iconTop + selectedIcon.intrinsicHeight
                    val iconRight = itemView.right - iconMargin
                    val iconLeft = iconRight - selectedIcon.intrinsicWidth

                    selectedIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    background.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                    )
                }
                else -> {
                    selectedIcon = null
                    background.setBounds(0,0,0,0)
                }
            }
            background.draw(c)
            selectedIcon.draw(c)
        }
    }
}

fun View.handleViewVisibility(show: Boolean) {
    visibility = if (show) {
        View.VISIBLE
    } else {
        View.GONE
    }
}

fun String.toToast(context: Context) {
    Toast.makeText(context, this, Toast.LENGTH_LONG).show()
}
