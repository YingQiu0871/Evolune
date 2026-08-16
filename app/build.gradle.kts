plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val evoluneApplicationId: String by rootProject.extra
val evoluneDebugSuffix: String by rootProject.extra
val evoluneVersionName: String by rootProject.extra
val evolunePhoneVersionCode: Int by rootProject.extra

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
    namespace = evoluneApplicationId
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
        minSdk = 31
        targetSdk = 36
        versionCode = evolunePhoneVersionCode
        versionName = evoluneVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }



    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":experience-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.graphics.path)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
