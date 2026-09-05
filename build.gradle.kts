// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

val evoluneApplicationId by extra("io.github.yingqiu0871.evolune")
val evoluneWearNamespace by extra("io.github.yingqiu0871.evolune.wear")
val evoluneDebugSuffix by extra(".debug")
val evoluneVersionName by extra("1.5.0")

val evoluneVersionMajor = 1
val evoluneVersionMinor = 5
val evoluneVersionPatch = 0
val evoluneVersionRevision = 0
val evoluneReleaseOrdinal =
    evoluneVersionMajor * 1_000_000 +
        evoluneVersionMinor * 10_000 +
        evoluneVersionPatch * 100 +
        evoluneVersionRevision
val evolunePhoneVersionCode by extra(100_000_000 + evoluneReleaseOrdinal)
val evoluneWearVersionCode by extra(1_100_000_000 + evoluneReleaseOrdinal)

tasks.register("validateEvoluneIdentityAndVersioning") {
    group = "verification"
    description = "Validates shared Phone/Wear identity and version-code policy."

    doLast {
        val phoneCode = evolunePhoneVersionCode
        val wearCode = evoluneWearVersionCode
        val maximumPlayVersionCode = 2_100_000_000
        val priorV1VersionCode = 10_060

        check(evoluneApplicationId == "io.github.yingqiu0871.evolune")
        check(evoluneWearNamespace == "io.github.yingqiu0871.evolune.wear")
        check("$evoluneApplicationId$evoluneDebugSuffix" == "io.github.yingqiu0871.evolune.debug")
        check(evoluneVersionName == "1.5.0")
        check(phoneCode != wearCode)
        check(phoneCode > 0 && wearCode > 0)
        check(phoneCode <= maximumPlayVersionCode && wearCode <= maximumPlayVersionCode)
        check(phoneCode in 100_000_000 until 1_000_000_000)
        check(wearCode in 1_100_000_000 until 2_000_000_000)
        check(phoneCode > priorV1VersionCode && wearCode > priorV1VersionCode)
        check(phoneCode == 101_050_000)
        check(wearCode == 1_101_050_000)
    }
}
