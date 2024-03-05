plugins {
    kotlin("multiplatform") apply false
    `kotlin-dsl` apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    id("maven-publish")
}

val versionVal = libs.versions.kilua.rpc.get()

allprojects {
    group = "dev.kilua"
    if (hasProperty("SNAPSHOT")) {
        version = "$versionVal-SNAPSHOT"
    } else {
        version = versionVal
    }
}

nmcp {
    publishAggregation {
        project(":modules:kilua-rpc-annotations")
        project(":modules:kilua-rpc-types")
        project(":modules:kilua-rpc")
        project(":modules:kilua-rpc-ktor")
        project(":plugins:kilua-rpc-gradle-plugin")
        project(":plugins:kilua-rpc-ksp-processor")
        username = findProperty("mavenCentralUsername")?.toString()
        password = findProperty("mavenCentralPassword")?.toString()
        publicationType = "USER_MANAGED"
    }
}