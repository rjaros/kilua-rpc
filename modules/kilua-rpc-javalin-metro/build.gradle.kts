plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
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
    android {
        namespace = "dev.kilua.rpc"
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk
        compilerOptions {
            jvmTarget.set(androidJvmTarget)
        }
    }
    kotlinNativeTargets()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":modules:kilua-rpc-javalin"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }
        getByName("jvmMain") {
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

setupDokka()
setupPublishing()
