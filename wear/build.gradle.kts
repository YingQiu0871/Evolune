plugins {
    alias(libs.plugins.android.application)
}

val evoluneApplicationId: String by rootProject.extra
val evoluneWearNamespace: String by rootProject.extra
val evoluneDebugSuffix: String by rootProject.extra
val evoluneVersionName: String by rootProject.extra
val evoluneWearVersionCode: Int by rootProject.extra

val releaseSigningVariableNames = listOf(
    "EVOLUNE_KEYSTORE_PATH",
    "EVOLUNE_KEYSTORE_PASSWORD",
    "EVOLUNE_KEY_ALIAS",
    "EVOLUNE_KEY_PASSWORD"
)
val releaseSigningEnvironment = releaseSigningVariableNames.associateWith { System.getenv(it) }
val missingReleaseSigningVariables = releaseSigningEnvironment
    .filterValues { it.isNullOrBlank() }
    .keys
val hasReleaseSigningCredentials = missingReleaseSigningVariables.isEmpty()
val releaseSigningProject = project

gradle.taskGraph.whenReady {
    val releaseTaskRequested = allTasks.any { task ->
        task.project == releaseSigningProject && task.name.contains("release", ignoreCase = true)
    }
    if (releaseTaskRequested && !hasReleaseSigningCredentials) {
        throw GradleException(
            "Evolune Release signing credentials are missing: " +
                missingReleaseSigningVariables.sorted().joinToString() +
                ". Configure the approved external environment variables; Release never uses debug signing."
        )
    }
    if (releaseTaskRequested && !file(requireNotNull(releaseSigningEnvironment["EVOLUNE_KEYSTORE_PATH"])).isFile) {
        throw GradleException("EVOLUNE_KEYSTORE_PATH must point to an existing persistent release keystore.")
    }
}

android {
    namespace = evoluneWearNamespace
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningEnvironment["EVOLUNE_KEYSTORE_PATH"]))
                storePassword = requireNotNull(releaseSigningEnvironment["EVOLUNE_KEYSTORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningEnvironment["EVOLUNE_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningEnvironment["EVOLUNE_KEY_PASSWORD"])
            }
        }
    }

    defaultConfig {
        applicationId = evoluneApplicationId
        minSdk = 30
        targetSdk = 36
        versionCode = evoluneWearVersionCode
        versionName = evoluneVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            applicationIdSuffix = evoluneDebugSuffix
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":experience-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
