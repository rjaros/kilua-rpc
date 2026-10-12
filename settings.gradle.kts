@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        // Needed by the Android Gradle plugin, which is not published to the plugin portal.
        google()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

// The Gradle plugin is developed in this repository but consumed by the examples as a plugin. It has
// to be a top-level included build, not one under pluginManagement: a pluginManagement include only
// serves plugin resolution, and Gradle does not apply the included build's dependency substitutions
// (gradle/gradle#30059) - so the coordinate the root `nmcpAggregation` uses would resolve from a
// repository instead of this source. A top-level include covers both the `plugins {}` resolution in
// the examples and the substitution of `dev.kilua:kilua-rpc-gradle-plugin`.
includeBuild("plugins/kilua-rpc-gradle-plugin")

include(":modules:kilua-rpc-annotations")
include(":modules:kilua-rpc-types")
include(":modules:kilua-rpc-core")
include(":modules:kilua-rpc-javalin")
include(":modules:kilua-rpc-javalin-koin")
include(":modules:kilua-rpc-javalin-metro")
include(":modules:kilua-rpc-jooby")
include(":modules:kilua-rpc-jooby-koin")
include(":modules:kilua-rpc-jooby-metro")
include(":modules:kilua-rpc-ktor")
include(":modules:kilua-rpc-ktor-koin")
include(":modules:kilua-rpc-ktor-metro")
include(":modules:kilua-rpc-metro")
include(":modules:kilua-rpc-micronaut")
include(":modules:kilua-rpc-quarkus")
include(":modules:kilua-rpc-spring-boot")
include(":modules:kilua-rpc-vertx")
include(":modules:kilua-rpc-vertx-koin")
include(":modules:kilua-rpc-vertx-metro")
include(":plugins:kilua-rpc-ksp-processor")
include(":examples:fullstack-javalin")
include(":examples:fullstack-javalin-koin")
include(":examples:fullstack-javalin-metro")
include(":examples:fullstack-jooby")
include(":examples:fullstack-jooby-koin")
include(":examples:fullstack-jooby-metro")
include(":examples:fullstack-ktor")
include(":examples:fullstack-ktor-koin")
include(":examples:fullstack-ktor-metro")
include(":examples:fullstack-micronaut")
include(":examples:fullstack-micronaut:application")
include(":examples:fullstack-quarkus")
include(":examples:fullstack-quarkus:application")
include(":examples:fullstack-spring-boot")
include(":examples:fullstack-spring-boot:application")
include(":examples:fullstack-vertx")
include(":examples:fullstack-vertx-koin")
include(":examples:fullstack-vertx-metro")
include(":examples:typescript-ktor:ktor-server")
include(":examples:single-target-linux")
