@file:Suppress("UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.content.Context

internal object WearAppSnapshotRevisionStore {
    private const val PREFERENCES_NAME = "wear_dashboard_cache"
    private const val KEY_REVISION = "wear_app_snapshot_revision"

    @Synchronized
    fun next(context: Context): Long {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val current = preferences.getLong(KEY_REVISION, 0L)
        check(current < Long.MAX_VALUE) { "Wear App snapshot revision exhausted" }
        val next = current + 1L
        check(preferences.edit().putLong(KEY_REVISION, next).commit()) {
            "Unable to persist Wear App snapshot revision"
        }
        return next
    }
}
