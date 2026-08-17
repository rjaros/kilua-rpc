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
        getByName("commonMain") {
            dependencies {
                api(project(":modules:kilua-rpc-jooby"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }
        getByName("jvmMain") {
            dependencies {
                api(libs.koin.core)
                api(libs.koin.logger.slf4j)
            }
        }
    }
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
