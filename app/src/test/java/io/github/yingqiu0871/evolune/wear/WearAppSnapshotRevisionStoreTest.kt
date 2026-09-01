package io.github.yingqiu0871.evolune.wear

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAppSnapshotRevisionStoreTest {
    @Test
    fun `reservation precedes capture and older capture is older when it completes last`() = runBlocking {
        var nextRevision = 0L
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()

        val oldCapture = async {
            withReservedWearAppSnapshotRevision(
                reserveRevision = { ++nextRevision }
            ) { revision ->
                oldStarted.complete(Unit)
                releaseOld.await()
                revision
            }
        }
        oldStarted.await()

        val newCapture = async {
            withReservedWearAppSnapshotRevision(
                reserveRevision = { ++nextRevision }
            ) { revision -> revision }
        }
        val newRevision = newCapture.await()
        releaseOld.complete(Unit)
        val oldRevision = oldCapture.await()

        assertTrue(oldRevision < newRevision)
        assertEquals(1L, oldRevision)
        assertEquals(2L, newRevision)
    }
}
