package io.github.yingqiu0871.evolune.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAppRefreshPolicyTest {
    @Test
    fun `snapshot refresh follows applied and duplicate authoritative responses`() {
        assertTrue(shouldRefreshAfterSnapshot(WearAppSnapshotApplyResult.Applied))
        assertTrue(shouldRefreshAfterSnapshot(WearAppSnapshotApplyResult.Duplicate))
        assertFalse(shouldRefreshAfterSnapshot(WearAppSnapshotApplyResult.Older))
        assertFalse(shouldRefreshAfterSnapshot(WearAppSnapshotApplyResult.Rejected))
    }

    @Test
    fun `result refresh excludes rejected payloads but keeps applied and duplicate cleanup`() {
        assertTrue(shouldRefreshAfterResult(WearAppResultApply.Applied))
        assertTrue(shouldRefreshAfterResult(WearAppResultApply.Duplicate))
        assertFalse(shouldRefreshAfterResult(WearAppResultApply.Rejected))
    }
}
