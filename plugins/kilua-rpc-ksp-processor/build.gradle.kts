plugins {
    kotlin("multiplatform")
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
}

kotlin {
    explicitApi()
    compilerOptions()
    kotlinJvmTargets()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":modules:kilua-rpc-annotations"))
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(libs.ksp.symbol.processing.api)
            }
        }
    }
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()
