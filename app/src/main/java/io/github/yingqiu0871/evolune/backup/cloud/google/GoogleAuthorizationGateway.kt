package io.github.yingqiu0871.evolune.backup.cloud.google

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationOperationErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationOperationResult
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationResolution
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationGateway
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

object GoogleAuthorizationRequestFactory {
    fun requestSpec() = GoogleDriveAuthorizationContract.requestSpec()

    fun build(): AuthorizationRequest {
        val contract = requestSpec()
        return AuthorizationRequest.builder()
            .setRequestedScopes(contract.requestedScopes.map(::Scope))
            .build()
    }
}

data class GoogleAuthorizationResolution(
    val pendingIntent: PendingIntent
) : AuthorizationResolution

/**
 * Platform-only Google Identity adapter. It reports a resolution requirement;
 * it never launches a PendingIntent and never stores a token outside memory.
 */
class GoogleAuthorizationGateway(
    context: Context,
    private val client: AuthorizationClient = Identity.getAuthorizationClient(context)
) : CloudAuthorizationGateway {
    private var currentToken: String? = null

    override suspend fun authorize(): CloudAuthorizationOutcome {
        currentToken?.let { return CloudAuthorizationOutcome.Authorized(it) }
        return try {
            val result = client.authorize(GoogleAuthorizationRequestFactory.build()).awaitTask()
            result.toOutcome()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                CloudAuthorizationOutcome.Cancelled
            } else {
                CloudAuthorizationOutcome.Error(AuthorizationErrorCode.FAILED)
            }
        } catch (_: Exception) {
            CloudAuthorizationOutcome.Error(AuthorizationErrorCode.FAILED)
        }
    }

    override suspend fun clearToken(accessToken: String): AuthorizationOperationResult = try {
        client.clearToken(
            ClearTokenRequest.builder()
                .setToken(accessToken)
                .build()
        ).awaitTask()
        if (currentToken == accessToken) currentToken = null
        AuthorizationOperationResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (currentToken == accessToken) currentToken = null
        AuthorizationOperationResult.Failure(AuthorizationOperationErrorCode.FAILED)
    }

    override suspend fun disconnect(): AuthorizationOperationResult {
        // Disconnect is a current-session operation; do not revoke the user's grant.
        val token = currentToken ?: return AuthorizationOperationResult.Success
        return clearToken(token)
    }

    /** B4 may launch this resolution and feed its result back later. */
    fun outcomeFromIntent(intent: Intent): CloudAuthorizationOutcome = try {
        client.getAuthorizationResultFromIntent(intent).toOutcome()
    } catch (e: ApiException) {
        if (e.statusCode == CommonStatusCodes.CANCELED) {
            CloudAuthorizationOutcome.Cancelled
        } else {
            CloudAuthorizationOutcome.Error(AuthorizationErrorCode.FAILED)
        }
    } catch (_: Exception) {
        CloudAuthorizationOutcome.Error(AuthorizationErrorCode.FAILED)
    }

    private fun AuthorizationResult.toOutcome(): CloudAuthorizationOutcome {
        val accessToken = getAccessToken()
        if (!accessToken.isNullOrBlank()) {
            currentToken = accessToken
            return CloudAuthorizationOutcome.Authorized(accessToken)
        }
        val pendingIntent = getPendingIntent()
        if (hasResolution() && pendingIntent != null) {
            return CloudAuthorizationOutcome.UserResolutionRequired(
                GoogleAuthorizationResolution(pendingIntent)
            )
        }
        return CloudAuthorizationOutcome.Error(AuthorizationErrorCode.TOKEN_UNAVAILABLE)
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            continuation.resume(task.result) { _, _, _ -> }
        } else {
            continuation.resumeWith(
                Result.failure(task.exception ?: IllegalStateException("Google task failed"))
            )
        }
    }
}
