plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    alias(libs.plugins.ksp)
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
                api(libs.kotlinx.coroutines.jdk8)
                api(libs.kotlinx.coroutines.reactor)
                api(libs.kotlinx.coroutines.reactive)
                api(project.dependencies.platform(libs.micronaut.platform))
                api("io.micronaut:micronaut-inject")
                api("io.micronaut:micronaut-http")
                api("io.micronaut:micronaut-router")
                api("io.micronaut:micronaut-websocket")
                api("io.micronaut.reactor:micronaut-reactor")
                api(libs.logback.classic)
            }
        }
    }
}

dependencies {
    add("kspJvm", platform(libs.micronaut.platform))
    add("kspJvm", "io.micronaut:micronaut-inject-kotlin")
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
