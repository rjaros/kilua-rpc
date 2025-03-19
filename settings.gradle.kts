@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

include(":modules:kilua-rpc-annotations")
include(":modules:kilua-rpc-types")
include(":modules:kilua-rpc-core")
include(":modules:kilua-rpc-javalin-common")
include(":modules:kilua-rpc-javalin")
include(":modules:kilua-rpc-javalin-koin")
include(":modules:kilua-rpc-jooby-common")
include(":modules:kilua-rpc-jooby")
include(":modules:kilua-rpc-jooby-koin")
include(":modules:kilua-rpc-ktor-common")
include(":modules:kilua-rpc-ktor")
include(":modules:kilua-rpc-ktor-koin")
//include(":modules:kilua-rpc-micronaut")
include(":modules:kilua-rpc-spring-boot")
include(":modules:kilua-rpc-vertx-common")
include(":modules:kilua-rpc-vertx")
include(":modules:kilua-rpc-vertx-koin")
include(":plugins:kilua-rpc-gradle-plugin")
include(":plugins:kilua-rpc-ksp-processor")
include(":examples:fullstack-javalin")
include(":examples:fullstack-javalin-koin")
include(":examples:fullstack-jooby")
include(":examples:fullstack-jooby-koin")
include(":examples:fullstack-ktor")
include(":examples:fullstack-ktor-koin")
//include(":examples:fullstack-micronaut")
//include(":examples:fullstack-micronaut:application")
include(":examples:fullstack-spring-boot")
include(":examples:fullstack-spring-boot:application")
include(":examples:fullstack-vertx")
include(":examples:fullstack-vertx-koin")
