plugins {
    alias(libs.plugins.android.application)
}

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
    namespace = "io.github.yingqiu0871.evolune.wear"
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
        applicationId = "io.github.yingqiu0871.evolune.wear"
        minSdk = 30
        targetSdk = 36
        versionCode = 10060
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)
    testImplementation(libs.junit)
}
