plugins {
    kotlin("multiplatform") apply false
    `kotlin-dsl` apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
}

val versionVal = libs.versions.kilua.rpc.asProvider().get()

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
        project(":modules:kilua-rpc-core")
        project(":modules:kilua-rpc-javalin")
        project(":modules:kilua-rpc-jooby")
        project(":modules:kilua-rpc-ktor-guice")
        project(":modules:kilua-rpc-ktor-koin")
        project(":modules:kilua-rpc-micronaut")
        project(":modules:kilua-rpc-spring-boot")
        project(":modules:kilua-rpc-vertx")
        project(":plugins:kilua-rpc-gradle-plugin")
        project(":plugins:kilua-rpc-ksp-processor")
        endpoint.set("https://central.sonatype.com/api/v1/publisher/upload")
        username = findProperty("mavenCentralUsername")?.toString()
        password = findProperty("mavenCentralPassword")?.toString()
        publicationType = "USER_MANAGED"
    }
}
dependencies {
    dokka(project(":modules:kilua-rpc-annotations"))
    dokka(project(":modules:kilua-rpc-core"))
    dokka(project(":modules:kilua-rpc-javalin"))
    dokka(project(":modules:kilua-rpc-jooby"))
    dokka(project(":modules:kilua-rpc-ktor-guice"))
    dokka(project(":modules:kilua-rpc-ktor-koin"))
    dokka(project(":modules:kilua-rpc-micronaut"))
    dokka(project(":modules:kilua-rpc-spring-boot"))
    dokka(project(":modules:kilua-rpc-vertx"))
    dokka(project(":modules:kilua-rpc-types"))
}
