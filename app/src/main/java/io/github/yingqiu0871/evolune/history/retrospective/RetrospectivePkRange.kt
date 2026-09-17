package io.github.yingqiu0871.evolune.history.retrospective

/**
 * v1.7.1 UI hotfix — the selectable retrospective window length.
 *
 * The window stays a plain `[capturedAt − days, capturedAt]` query into the unchanged
 * retrospective PK authority (C-01 equations, C-04 orchestration); only the queried length is
 * user-selectable. `LAST_7_DAYS` is the default the surface opens with.
 */
enum class RetrospectivePkRange(val days: Long) {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_90_DAYS(90)
}
