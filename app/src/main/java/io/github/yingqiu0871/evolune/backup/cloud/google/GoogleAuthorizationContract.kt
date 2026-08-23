package io.github.yingqiu0871.evolune.backup.cloud.google

const val GOOGLE_DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

data class AuthorizationRequestSpec(
    val requestedScopes: List<String>,
    val offlineAccess: Boolean
)

object GoogleDriveAuthorizationContract {
    fun requestSpec(): AuthorizationRequestSpec = AuthorizationRequestSpec(
        requestedScopes = listOf(GOOGLE_DRIVE_APPDATA_SCOPE),
        offlineAccess = false
    )
}
