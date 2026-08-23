package io.github.yingqiu0871.evolune.data

import android.content.Context
import io.github.yingqiu0871.evolune.backup.FileRestoreJournalStore
import io.github.yingqiu0871.evolune.backup.RestoreRecoveryResult
import io.github.yingqiu0871.evolune.backup.RestoreTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Recovery is completed before MainActivity constructs repository consumers or
 * starts any reminder/widget/Wear effects.
 */
internal fun recoverInterruptedRestoreAtStartup(
    context: Context,
    settingsStore: SettingsDataStore
): RestoreRecoveryResult = runBlocking(Dispatchers.IO) {
    RestoreTransaction(
        persistence = RoomRestorePersistence(
            database = AppDatabase.getDatabase(context),
            settingsStore = settingsStore,
            atomicSettingsStore = settingsStore
        ),
        journalStore = FileRestoreJournalStore(context)
    ).recoverInterruptedRestoreIfNeeded()
}
