package io.github.yingqiu0871.evolune.theme.palette

/**
 * v1.7.2 Slice A — the immutable light/dark seed tokens of one preset palette.
 *
 * Values are ARGB longs. This is data only: how a surface renders them (widget RemoteViews,
 * future App Material schemes) is a consumer concern.
 */
data class PaletteSeed(
    val lightSurface: Long,
    val lightPrimary: Long,
    val lightSecondary: Long,
    val lightTertiary: Long,
    val lightContainer: Long,
    val lightOnContainer: Long,
    val darkSurface: Long,
    val darkPrimary: Long,
    val darkSecondary: Long,
    val darkTertiary: Long,
    val darkContainer: Long
)
