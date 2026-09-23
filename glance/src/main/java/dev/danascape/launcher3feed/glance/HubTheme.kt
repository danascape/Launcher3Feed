package dev.danascape.launcher3feed.glance

import android.content.Context
import android.content.res.Configuration

/**
 * The Material colour roles the hub's edit mode uses, taken from the system palette.
 *
 * The hub is Compose and reads `MaterialTheme.colorScheme`, which on Android is derived from the
 * platform's dynamic palette. Rather than depend on the Material library's theme attributes, this
 * reads the same palette the framework publishes as `android.R.color.system_accent*`, so the panel
 * follows the wallpaper exactly as the hub does and needs no theming on the host context.
 *
 * Roles, matched to where the hub uses them:
 *  - toolbar filled button: [primary] on [onPrimary] (`filledButtonColors`)
 *  - toolbar outlined button: [primary] for text and its 2dp border
 *  - selected widget outline: [primary] (`ResizeableItemFrame`'s default `outlineColor`)
 *  - resize handle: [tertiaryContainer], marked with [onTertiaryContainer]
 *
 * The tone pairs are Material 3's standard dynamic-colour mapping, picked for the panel's current
 * light or dark configuration.
 */
class HubTheme
private constructor(
    val primary: Int,
    val onPrimary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
) {
    companion object {
        fun from(context: Context): HubTheme {
            val dark =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES

            return if (dark) {
                HubTheme(
                    primary = context.getColor(android.R.color.system_accent1_200),
                    onPrimary = context.getColor(android.R.color.system_accent1_800),
                    tertiaryContainer = context.getColor(android.R.color.system_accent3_700),
                    onTertiaryContainer = context.getColor(android.R.color.system_accent3_100),
                )
            } else {
                HubTheme(
                    primary = context.getColor(android.R.color.system_accent1_600),
                    onPrimary = context.getColor(android.R.color.system_accent1_0),
                    tertiaryContainer = context.getColor(android.R.color.system_accent3_100),
                    onTertiaryContainer = context.getColor(android.R.color.system_accent3_900),
                )
            }
        }
    }
}
