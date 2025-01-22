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
        val jsMain by getting {
            dependencies {
            }
        }
        val wasmJsMain by getting {
            dependencies {
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("reflect"))
                api(libs.kotlinx.coroutines.reactor)
                api(libs.spring.boot.starter)
                api(libs.spring.boot.starter.webflux)
                api(libs.spring.boot.starter.security)
                api(libs.logback.classic)
            }
        }
    }
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()

nmcp {
    publishAllPublications {}
}
