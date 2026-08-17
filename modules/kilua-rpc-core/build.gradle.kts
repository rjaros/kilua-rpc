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
        getByName("commonMain") {
            dependencies {
                api(project(":modules:kilua-rpc-types"))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.test.balloon)
            }
        }
        getByName("webMain") {
            dependencies {
                api(libs.wrappers.browser)
            }
        }
    }
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
