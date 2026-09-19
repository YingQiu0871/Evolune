package io.github.yingqiu0871.evolune.navigation

/**
 * v1.7.3 — single source of truth for primary navigation chrome visibility.
 *
 * The full-screen child destinations hide the app's primary navigation chrome
 * (bottom NavigationBar in compact windows, NavigationRail otherwise). Route
 * changes across that boundary must not run the shared page fade/scale motion:
 * the chrome appearing/disappearing changes the NavHost container geometry in
 * the same frame, which made the page look like it was expanding/shrinking and
 * produced deterministic stutter on the released v1.7.2 build.
 *
 * Both the chrome visibility itself and the NavHost transition suppression use
 * this policy, so the route set is never duplicated.
 */
internal object PrimaryNavigationChrome {

    val FULL_SCREEN_ROUTES: Set<String> = setOf(
        ABOUT_ROUTE,
        GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE,
        ONBOARDING_ROUTE,
        DISCLOSURES_ROUTE,
        FEATURE_TUTORIAL_ROUTE,
        INSIGHTS_ROUTE,
        RETROSPECTIVE_ROUTE,
        TIMELINE_ROUTE
    )

    /** True when the route shows the primary navigation chrome (bar/rail). */
    fun shows(route: String?): Boolean = route !in FULL_SCREEN_ROUTES

    /** True when navigating between these routes changes chrome visibility. */
    fun changes(fromRoute: String?, toRoute: String?): Boolean =
        shows(fromRoute) != shows(toRoute)
}

internal const val ABOUT_ROUTE = "settings_about"
internal const val GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE = "google_drive_backup_restore"
internal const val ONBOARDING_ROUTE = "onboarding"
internal const val DISCLOSURES_ROUTE = "disclosures"
internal const val FEATURE_TUTORIAL_ROUTE = "feature_tutorial"
internal const val INSIGHTS_ROUTE = "insights"
internal const val RETROSPECTIVE_ROUTE = "retrospective"
internal const val TIMELINE_ROUTE = "timeline"
