package io.github.yingqiu0871.evolune.core.dataapi

sealed interface InsertResult {
    data object Inserted : InsertResult
    data object Idempotent : InsertResult
    data object Conflict : InsertResult
    data object Invalid : InsertResult
}

sealed interface UpdateResult {
    data object Updated : UpdateResult
    data object NoChange : UpdateResult
    data object NotFound : UpdateResult
    data object RevisionConflict : UpdateResult
    data object Invalid : UpdateResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotFound : DeleteResult
}

sealed interface PlanSaveResult {
    data object Created : PlanSaveResult
    data object Updated : PlanSaveResult
    data object NoChange : PlanSaveResult
    data object Invalid : PlanSaveResult
}

sealed interface PlanUpdateResult {
    data object Updated : PlanUpdateResult
    data object NoChange : PlanUpdateResult
    data object NotFound : PlanUpdateResult
    data object Invalid : PlanUpdateResult
}
