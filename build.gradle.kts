plugins {
    kotlin("multiplatform") apply false
    `kotlin-dsl` apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.nmcp) apply false
    alias(libs.plugins.nmcp.aggregation)
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

nmcpAggregation {
    centralPortal {
        username = findProperty("mavenCentralUsername")?.toString()
        password = findProperty("mavenCentralPassword")?.toString()
        publishingType = "USER_MANAGED"
        publicationName = "Kilua RPC $version"
    }
}

dependencies {
    nmcpAggregation(project(":modules:kilua-rpc-annotations"))
    nmcpAggregation(project(":modules:kilua-rpc-types"))
    nmcpAggregation(project(":modules:kilua-rpc-core"))
    nmcpAggregation(project(":modules:kilua-rpc-javalin-common"))
    nmcpAggregation(project(":modules:kilua-rpc-javalin"))
    nmcpAggregation(project(":modules:kilua-rpc-javalin-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-jooby-common"))
    nmcpAggregation(project(":modules:kilua-rpc-jooby"))
    nmcpAggregation(project(":modules:kilua-rpc-jooby-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-ktor-common"))
    nmcpAggregation(project(":modules:kilua-rpc-ktor"))
    nmcpAggregation(project(":modules:kilua-rpc-ktor-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-micronaut"))
    nmcpAggregation(project(":modules:kilua-rpc-spring-boot"))
    nmcpAggregation(project(":modules:kilua-rpc-vertx-common"))
    nmcpAggregation(project(":modules:kilua-rpc-vertx"))
    nmcpAggregation(project(":modules:kilua-rpc-vertx-koin"))
    nmcpAggregation(project(":plugins:kilua-rpc-gradle-plugin"))
    nmcpAggregation(project(":plugins:kilua-rpc-ksp-processor"))
    dokka(project(":modules:kilua-rpc-annotations"))
    dokka(project(":modules:kilua-rpc-core"))
    dokka(project(":modules:kilua-rpc-javalin-common"))
    dokka(project(":modules:kilua-rpc-javalin"))
    dokka(project(":modules:kilua-rpc-javalin-koin"))
    dokka(project(":modules:kilua-rpc-jooby-common"))
    dokka(project(":modules:kilua-rpc-jooby"))
    dokka(project(":modules:kilua-rpc-jooby-koin"))
    dokka(project(":modules:kilua-rpc-ktor-common"))
    dokka(project(":modules:kilua-rpc-ktor"))
    dokka(project(":modules:kilua-rpc-ktor-koin"))
    dokka(project(":modules:kilua-rpc-micronaut"))
    dokka(project(":modules:kilua-rpc-spring-boot"))
    dokka(project(":modules:kilua-rpc-vertx-common"))
    dokka(project(":modules:kilua-rpc-vertx"))
    dokka(project(":modules:kilua-rpc-vertx-koin"))
    dokka(project(":modules:kilua-rpc-types"))
}
