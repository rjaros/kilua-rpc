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

rootProject.name = "kilua-rpc-project"
include(":modules:kilua-rpc-annotations")
include(":modules:kilua-rpc-types")
include(":modules:kilua-rpc")
include(":modules:kilua-rpc-ktor")
include(":plugins:kilua-rpc-gradle-plugin")
include(":plugins:kilua-rpc-ksp-processor")
