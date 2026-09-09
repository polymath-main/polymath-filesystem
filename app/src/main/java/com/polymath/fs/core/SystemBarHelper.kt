package com.polymath.fs.core

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * SystemBarHelper coordinates dynamic edge-to-edge system insets
 * and adaptive status bar icon contrasts (light/dark) seamlessly
 * across all themes and fragments using WindowInsetsControllerCompat.
 */
object SystemBarHelper {

    /**
     * Updates status bar and navigation bar icon contrast dynamically based on current theme background luminosity.
     */
    fun updateSystemBarAppearance(activity: Activity, customColor: Int? = null) {
        val window = activity.window
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        val targetColor = customColor ?: ThemeManager.getThemeColors(activity).background
        val isLightBackground = ColorUtils.calculateLuminance(targetColor) > 0.5

        insetsController.isAppearanceLightStatusBars = isLightBackground
        insetsController.isAppearanceLightNavigationBars = isLightBackground

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    /**
     * Dynamically adjusts status bar icon contrast based on the background color of glassmorphic header components.
     */
    fun adjustSystemBarContrastForHeader(activity: Activity, headerView: View) {
        val window = activity.window
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        val themeColors = ThemeManager.getThemeColors(activity)
        val isLightHeader = ColorUtils.calculateLuminance(themeColors.surface) > 0.5

        insetsController.isAppearanceLightStatusBars = isLightHeader
        insetsController.isAppearanceLightNavigationBars = isLightHeader

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    /**
     * Attaches dynamic insets listener to an AppBarLayout or top header view
     * to ensure it seamlessly adapts padding to status bar height across device rotations,
     * punch-holes, and display cutouts.
     */
    fun applyDynamicStatusBarInsets(targetHeaderView: View) {
        val initialPaddingTop = targetHeaderView.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(targetHeaderView) { v, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                statusBars.top + initialPaddingTop,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
    }

    /**
     * Applies bottom insets to bottom navigation or bottom sheet to adapt dynamically
     * to system navigation gesture pill or 3-button navigation bar.
     */
    fun applyDynamicNavigationBarInsets(bottomNavView: View) {
        val initialPaddingBottom = bottomNavView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavView) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navBars.bottom + initialPaddingBottom
            )
            insets
        }
    }
}
