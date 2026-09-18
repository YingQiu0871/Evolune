package io.github.yingqiu0871.evolune.theme.palette

/**
 * v1.7.2 Slice A — the ONE token authority for the eight widget preset palettes.
 *
 * The values are a byte-for-byte relocation of the released v1.7.1 phone-widget preset seeds
 * (`WidgetAppearance.PRESETS`); they must never be altered to satisfy another surface. The
 * widget renderer delegates here, and future App preset schemes consume the same seeds.
 */
object PaletteCatalog {

    /** Stable persisted ids, in selector order. */
    val stableIds: List<String> = PresetPalette.entries.map { it.name }

    fun seed(preset: PresetPalette): PaletteSeed = when (preset) {
        PresetPalette.MONET_BLUE -> PaletteSeed(
            0xFFF8F9FF, 0xFF3F5F90, 0xFF565F71, 0xFF705575, 0xFFD6E3FF, 0xFF0B1B33,
            0xFF111318, 0xFFA8C7FA, 0xFFBEC6DC, 0xFFDDBCE0, 0xFF284777
        )

        PresetPalette.MONET_VIOLET -> PaletteSeed(
            0xFFFBF8FF, 0xFF70558F, 0xFF665A70, 0xFF815343, 0xFFEEDBFF, 0xFF29143F,
            0xFF151217, 0xFFDDB8FF, 0xFFD1C0D8, 0xFFF5B9A5, 0xFF573D74
        )

        PresetPalette.MONET_SAKURA -> PaletteSeed(
            0xFFFFF8F9, 0xFF9A405D, 0xFF75565F, 0xFF775930, 0xFFFFD9E2, 0xFF3E001D,
            0xFF181113, 0xFFFFB1C5, 0xFFE5BDC6, 0xFFE7C086, 0xFF7D2947
        )

        PresetPalette.MONET_MINT -> PaletteSeed(
            0xFFF6FCF7, 0xFF356A4E, 0xFF506355, 0xFF3F6374, 0xFFB8F2CE, 0xFF002112,
            0xFF0E1511, 0xFF9DD6B3, 0xFFB7CCBC, 0xFFA6CDDF, 0xFF1D5138
        )

        PresetPalette.MONET_TEAL -> PaletteSeed(
            0xFFF4FBF9, 0xFF006A64, 0xFF4A6360, 0xFF4A607C, 0xFF9DF2E9, 0xFF00201E,
            0xFF0E1514, 0xFF81D5CD, 0xFFB0CCC8, 0xFFB2C8E8, 0xFF00504B
        )

        PresetPalette.MONET_AMBER -> PaletteSeed(
            0xFFFFF9F0, 0xFF805600, 0xFF705D3E, 0xFF53643C, 0xFFFFDEA5, 0xFF291800,
            0xFF18130B, 0xFFF6BD6C, 0xFFDEC6A1, 0xFFBACD97, 0xFF614000
        )

        PresetPalette.MONET_NEUTRAL -> PaletteSeed(
            0xFFFAF9FC, 0xFF5F5E65, 0xFF616066, 0xFF605D6E, 0xFFE5E1E9, 0xFF1B1B1F,
            0xFF141316, 0xFFC9C5CD, 0xFFCBC5CD, 0xFFCAC3DB, 0xFF47464D
        )

        PresetPalette.MONET_LAVENDER -> PaletteSeed(
            0xFFFCF8FF, 0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFFEADDFF, 0xFF21005D,
            0xFF141218, 0xFFD0BCFF, 0xFFCCC2DC, 0xFFEFB8C8, 0xFF4F378B
        )
    }
}
