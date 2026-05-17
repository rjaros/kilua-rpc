plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.test.balloon)
}

kotlin {
    explicitApi()
    compilerOptions(withWasmMetadata = true)
    kotlinJsTargets()
    kotlinWasmTargets()
    kotlinJvmTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":modules:kilua-rpc-types"))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.test.balloon)
            }
        }
        val webMain by getting {
            dependencies {
                api(libs.wrappers.browser)
            }
        }
    }
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
