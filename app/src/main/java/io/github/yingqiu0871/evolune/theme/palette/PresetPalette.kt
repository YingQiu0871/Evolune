package io.github.yingqiu0871.evolune.theme.palette

/**
 * v1.7.2 Slice A — the eight canonical phone-widget preset palettes.
 *
 * Stable identity is the enum name; widget persistence stores exactly this name. The list and
 * the names are frozen: the phone widget enum keeps its nine values (these eight plus
 * `MATERIAL_YOU_AUTO`, which is a dynamics source, not a preset). `LEGACY_BUILTIN` is an App
 * compatibility identity and is deliberately NOT a member here.
 */
enum class PresetPalette {
    MONET_BLUE,
    MONET_VIOLET,
    MONET_SAKURA,
    MONET_MINT,
    MONET_TEAL,
    MONET_AMBER,
    MONET_NEUTRAL,
    MONET_LAVENDER;

    companion object {
        /** Resolves a stable preset id; unknown/null ids have no preset (caller decides). */
        fun fromId(id: String?): PresetPalette? = entries.firstOrNull { it.name == id }
    }
}
