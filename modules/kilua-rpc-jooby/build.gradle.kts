plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    id("com.android.kotlin.multiplatform.library")
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
    // The android flavour of this module is client only: androidMain holds a null-object route
    // registry and nothing else, so none of the Jooby server dependencies in jvmMain are published
    // for it. Everything the client needs comes from commonMain via kilua-rpc-core.
    android {
        namespace = "dev.kilua.rpc"
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk
        compilerOptions {
            jvmTarget.set(androidJvmTarget)
        }
        withHostTestBuilder {}.configure {}
    }
    kiluaHierarchyTemplate()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":modules:kilua-rpc-core"))
                api(project(":modules:kilua-rpc-annotations"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(kotlin("reflect"))
                api(libs.jooby.kotlin)
                api(libs.jooby.jackson)
                api(libs.logback.classic)
            }
        }
    }
}

setupDokka(tasks.dokkaGeneratePublicationHtml)
setupPublishing()
