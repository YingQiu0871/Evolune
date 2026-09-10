package io.github.yingqiu0871.evolune.wear

internal fun shouldRefreshAfterSnapshot(result: WearAppSnapshotApplyResult): Boolean =
    result == WearAppSnapshotApplyResult.Applied ||
        result == WearAppSnapshotApplyResult.Duplicate

internal fun shouldRefreshAfterResult(result: WearAppResultApply): Boolean =
    result != WearAppResultApply.Rejected
