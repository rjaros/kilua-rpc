plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
}

kotlin {
    explicitApi()
    compilerOptions()
    kotlinJsTargets()
    kotlinWasmTargets()
    kotlinJvmTargets()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":modules:kilua-rpc-core"))
                api(project(":modules:kilua-rpc-annotations"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("reflect"))
                api(libs.kotlinx.coroutines.jdk8)
                api(libs.javalin)
                api(libs.jackson.module.kotlin)
                api(libs.logback.classic)
            }
        }
    }
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
