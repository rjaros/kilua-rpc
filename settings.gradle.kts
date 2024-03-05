@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
        }
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
        }
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
