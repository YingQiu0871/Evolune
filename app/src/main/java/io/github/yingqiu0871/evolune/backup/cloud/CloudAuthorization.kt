package io.github.yingqiu0871.evolune.backup.cloud

interface AuthorizationResolution

sealed interface CloudAuthorizationOutcome {
    data class Authorized(val accessToken: String) : CloudAuthorizationOutcome

    data class UserResolutionRequired(val resolution: AuthorizationResolution) :
        CloudAuthorizationOutcome

    data object Cancelled : CloudAuthorizationOutcome

    data object Unavailable : CloudAuthorizationOutcome

    data class Error(val code: AuthorizationErrorCode) : CloudAuthorizationOutcome
}

enum class AuthorizationErrorCode {
    FAILED,
    TOKEN_UNAVAILABLE
}

enum class AuthorizationOperationErrorCode {
    FAILED,
    UNAVAILABLE
}

sealed interface AuthorizationOperationResult {
    data object Success : AuthorizationOperationResult

    data class Failure(val code: AuthorizationOperationErrorCode) : AuthorizationOperationResult
}

interface CloudAuthorizationGateway {
    suspend fun authorize(): CloudAuthorizationOutcome

    suspend fun clearToken(accessToken: String): AuthorizationOperationResult

    suspend fun disconnect(): AuthorizationOperationResult
}
