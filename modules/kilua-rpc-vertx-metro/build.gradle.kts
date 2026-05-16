plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    alias(libs.plugins.metro)
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
                api(project(":modules:kilua-rpc-vertx"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(project(":modules:kilua-rpc-metro"))
                implementation(libs.metro.runtime)
            }
        }
    }
}

metro {
    automaticallyAddRuntimeDependencies = false
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
