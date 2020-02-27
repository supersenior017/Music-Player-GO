package com.iven.musicplayergo.extensions

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.iven.musicplayergo.R
import com.iven.musicplayergo.fragments.DetailsFragment.Companion.DETAILS_FRAGMENT_TAG
import com.iven.musicplayergo.helpers.ThemeHelper
import kotlin.math.max

//viewTreeObserver extension to measure layout params
//https://antonioleiva.com/kotlin-ongloballayoutlistener/
inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
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

//extension to set menu items text color
fun MenuItem.setTitleColor(color: Int) {
    val hexColor = Integer.toHexString(color).substring(2)
    val html = "<font color='#$hexColor'>$title</font>"
    title = ThemeHelper.buildSpanned(html)
}

fun FragmentManager.addFragment(fragment: Fragment, tag: String?) {
    beginTransaction().apply {
        addToBackStack(null)
        add(
            R.id.container,
            fragment,
            tag
        )
        commit()
    }
}

fun FragmentManager.isDetailsFragment(remove: Boolean): Boolean {
    val df = findFragmentByTag(DETAILS_FRAGMENT_TAG)
    val isDetailsFragment = df != null && df.isVisible && df.isAdded
    if (remove && isDetailsFragment) df?.remove(this)
    return isDetailsFragment
}

private fun Fragment.remove(fragmentManager: FragmentManager) {
    fragmentManager.beginTransaction().apply {
        remove(this@remove)
        commit()
    }
}

fun View.createCircularReveal(isErrorFragment: Boolean, show: Boolean): Animator {

    val revealDuration: Long = if (isErrorFragment) 1500 else 500
    val radius = max(width, height).toFloat()

    val startRadius = if (show) 0f else radius
    val finalRadius = if (show) radius else 0f

    val cx = if (isErrorFragment) width / 2 else 0
    val cy = if (isErrorFragment) height / 2 else 0
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
                if (!show) visibility = View.GONE
            }
            start()
        }

    if (show) {

        val startColor = if (isErrorFragment) ContextCompat.getColor(
            context,
            R.color.red
        ) else ThemeHelper.resolveThemeAccent(context)

        val endColor = ThemeHelper.resolveColorAttr(
            context,
            android.R.attr.windowBackground
        )

        ValueAnimator().apply {
            setIntValues(startColor, endColor)
            setEvaluator(ArgbEvaluatorCompat())
            addUpdateListener { valueAnimator -> setBackgroundColor((valueAnimator.animatedValue as Int)) }
            duration = revealDuration
            if (isErrorFragment) doOnEnd {
                background =
                    ThemeHelper.createColouredRipple(
                        context,
                        ContextCompat.getColor(
                            context,
                            R.color.red
                        ),
                        R.drawable.ripple
                    )
            }
            start()
        }
    }
    return animator
}

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

fun View.handleViewVisibility(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.GONE
}

fun String.toToast(
    context: Context
) {
    Toast.makeText(context, this, Toast.LENGTH_LONG).show()
}
