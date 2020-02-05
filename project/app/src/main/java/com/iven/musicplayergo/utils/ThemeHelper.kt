package com.iven.musicplayergo.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.text.Html
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DividerItemDecoration
import com.iven.musicplayergo.MainActivity
import com.iven.musicplayergo.R
import com.iven.musicplayergo.RESTORE_SETTINGS_FRAGMENT
import com.iven.musicplayergo.goPreferences

object ThemeHelper {

    //update theme
    @JvmStatic
    fun applyNewThemeSmoothly(activity: Activity, restoreSettingsFragment: Boolean) {
        //smoothly set app theme
        Handler().postDelayed({
            Intent(activity, MainActivity::class.java).apply {
                putExtra(RESTORE_SETTINGS_FRAGMENT, restoreSettingsFragment)
                activity.finish()
                activity.startActivity(this)
            }
        }, 250)
    }

    @JvmStatic
    fun getDefaultNightMode(context: Context) = when (goPreferences.theme) {
        context.getString(R.string.theme_pref_light) -> AppCompatDelegate.MODE_NIGHT_NO
        context.getString(R.string.theme_pref_dark) -> AppCompatDelegate.MODE_NIGHT_YES
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
    }

    @JvmStatic
    fun resolveThemeIcon(context: Context) = when (goPreferences.theme) {
        context.getString(R.string.theme_pref_light) -> R.drawable.ic_day
        context.getString(R.string.theme_pref_auto) -> R.drawable.ic_auto
        else -> R.drawable.ic_night
    }

    @JvmStatic
    fun isDeviceLand(resources: Resources) =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    @JvmStatic
    private fun isThemeNight() =
        AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

    @JvmStatic
    fun getAlphaForAccent() = if (goPreferences.accent != R.color.yellow) 100 else 150

    @JvmStatic
    @TargetApi(Build.VERSION_CODES.O_MR1)
    fun handleLightSystemBars(view: View) {
        view.systemUiVisibility =
            if (isThemeNight()) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    //fixed array of pairs (first: accent, second: theme)
    @JvmStatic
    val accents = arrayOf(
        Triple(R.color.red, R.style.BaseTheme_Red, R.color.redPrimaryDark),
        Triple(R.color.pink, R.style.BaseTheme_Pink, R.color.pinkPrimaryDark),
        Triple(R.color.purple, R.style.BaseTheme_Purple, R.color.purplePrimaryDark),
        Triple(R.color.deep_purple, R.style.BaseTheme_DeepPurple, R.color.deepPurplePrimaryDark),
        Triple(R.color.indigo, R.style.BaseTheme_Indigo, R.color.indigoPrimaryDark),
        Triple(R.color.blue, R.style.BaseTheme_Blue, R.color.bluePrimaryDark),
        Triple(R.color.light_blue, R.style.BaseTheme_LightBlue, R.color.lightBluePrimaryDark),
        Triple(R.color.cyan, R.style.BaseTheme_Cyan, R.color.cyanPrimaryDark),
        Triple(R.color.teal, R.style.BaseTheme_Teal, R.color.tealPrimaryDark),
        Triple(R.color.green, R.style.BaseTheme_Green, R.color.greenPrimaryDark),
        Triple(R.color.light_green, R.style.BaseTheme_LightGreen, R.color.lightGreenPrimaryDark),
        Triple(R.color.lime, R.style.BaseTheme_Lime, R.color.limePrimaryDark),
        Triple(R.color.yellow, R.style.BaseTheme_Yellow, R.color.yellowPrimaryDark),
        Triple(R.color.amber, R.style.BaseTheme_Amber, R.color.amberPrimaryDark),
        Triple(R.color.orange, R.style.BaseTheme_Orange, R.color.orangePrimaryDark),
        Triple(R.color.deep_orange, R.style.BaseTheme_DeepOrange, R.color.deepOrangePrimaryDark),
        Triple(R.color.brown, R.style.BaseTheme_Brown, R.color.brownPrimaryDark),
        Triple(R.color.grey, R.style.BaseTheme_Grey, R.color.greyPrimaryDark),
        Triple(R.color.blue_grey, R.style.BaseTheme_BlueGrey, R.color.blueGreyPrimaryDark)
    )

    @JvmStatic
    @SuppressLint("DefaultLocale")
    fun getAccentName(accent: Int, context: Context): Spanned {
        val accentName = context.resources.getResourceEntryName(accent).replace(
            context.getString(R.string.underscore_delimiter),
            context.getString(R.string.space_delimiter)
        ).capitalize()
        return buildSpanned(
            context.getString(
                R.string.accent_and_hex,
                accentName,
                context.getString(accent).toUpperCase()
            )
        )
    }

    //finds theme and its position in accents array and returns a pair(theme, position)
    @JvmStatic
    fun getAccentedTheme() = try {
        val triple = accents.find { pair -> pair.first == goPreferences.accent }
        val theme = triple!!.second
        val position = accents.indexOf(triple)
        Pair(theme, position)
    } catch (e: Exception) {
        Pair(R.style.BaseTheme_DeepPurple, 3)
    }

    //finds theme and its position in accents array and returns a pair(theme, position)
    @JvmStatic
    fun resolvePrimaryDarkColor() = try {
        val triple = accents.find { pair -> pair.first == goPreferences.accent }
        triple!!.third
    } catch (e: Exception) {
        R.color.deepPurplePrimaryDark
    }

    @JvmStatic
    fun getColor(context: Context, color: Int, emergencyColor: Int) = try {
        ContextCompat.getColor(context, color)
    } catch (e: Exception) {
        ContextCompat.getColor(context, emergencyColor)
    }

    @JvmStatic
    fun updateIconTint(imageButton: ImageButton, tint: Int) {
        ImageViewCompat.setImageTintList(
            imageButton, ColorStateList.valueOf(tint)
        )
    }

    @ColorInt
    @JvmStatic
    fun resolveThemeAccent(context: Context): Int {
        var accent = goPreferences.accent

        //fallback to default color when the pref is f@#$ed (when resources change)
        if (!accents.map { accentId -> accentId.first }.contains(accent)) {
            accent = R.color.deep_purple
            goPreferences.accent = accent
        }
        return getColor(context, accent, R.color.deep_purple)
    }

    @ColorInt
    @JvmStatic
    fun resolveColorAttr(context: Context, @AttrRes colorAttr: Int): Int {
        val resolvedAttr: TypedValue = resolveThemeAttr(context, colorAttr)
        // resourceId is used if it's a ColorStateList, and data if it's a color reference or a hex color
        val colorRes =
            if (resolvedAttr.resourceId != 0) resolvedAttr.resourceId else resolvedAttr.data
        return ContextCompat.getColor(context, colorRes)
    }

    @JvmStatic
    private fun resolveThemeAttr(context: Context, @AttrRes attrRes: Int) =
        TypedValue().apply { context.theme.resolveAttribute(attrRes, this, true) }

    @JvmStatic
    fun getRecyclerViewDivider(context: Context) = DividerItemDecoration(
        context,
        DividerItemDecoration.VERTICAL
    ).apply {
        setDrawable(
            ColorDrawable(
                getAlphaAccent(
                    context,
                    if (isThemeNight()) 45 else 85
                )
            )
        )
    }

    @JvmStatic
    fun getAlphaAccent(context: Context, alpha: Int) =
        ColorUtils.setAlphaComponent(resolveThemeAccent(context), alpha)

    @JvmStatic
    fun getTabIcon(iconIndex: Int) = when (iconIndex) {
        0 -> R.drawable.ic_person
        1 -> R.drawable.ic_music_note
        2 -> R.drawable.ic_folder
        else -> R.drawable.ic_more_horiz
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun buildSpanned(res: String): Spanned = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> Html.fromHtml(
            res,
            Html.FROM_HTML_MODE_LEGACY
        )
        else -> Html.fromHtml(res)
    }

    @JvmStatic
    fun getPreciseVolumeIcon(volume: Int) = when (volume) {
        in 1..33 -> R.drawable.ic_volume_mute
        in 34..67 -> R.drawable.ic_volume_down
        in 68..100 -> R.drawable.ic_volume_up
        else -> R.drawable.ic_volume_off
    }

    @JvmStatic
    fun createColouredRipple(context: Context, rippleColor: Int, rippleId: Int): Drawable? {
        val ripple = AppCompatResources.getDrawable(context, rippleId) as RippleDrawable
        return ripple.apply {
            setColor(ColorStateList.valueOf(rippleColor))
        }
    }
}
