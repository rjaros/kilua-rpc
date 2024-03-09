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
include(":modules:kilua-rpc-javalin")
include(":modules:kilua-rpc-jooby")
include(":modules:kilua-rpc-ktor-guice")
include(":modules:kilua-rpc-ktor-koin")
include(":modules:kilua-rpc-micronaut")
include(":modules:kilua-rpc-spring-boot")
include(":modules:kilua-rpc-vertx")
include(":plugins:kilua-rpc-gradle-plugin")
include(":plugins:kilua-rpc-ksp-processor")
include(":examples:fullstack-javalin")
include(":examples:fullstack-jooby")
include(":examples:fullstack-ktor-koin")
include(":examples:fullstack-ktor-guice")
include(":examples:fullstack-micronaut")
include(":examples:fullstack-spring-boot")
include(":examples:fullstack-vertx")
