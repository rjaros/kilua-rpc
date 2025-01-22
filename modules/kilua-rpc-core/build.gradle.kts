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
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val wasmJsMain by getting {
            dependencies {
                api(libs.kotlinx.browser)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.hamcrest)
                implementation(libs.testng)
            }
        }
    }
}

tasks.withType<Test> {
    useTestNG()
}

setupDokka(tasks.dokkaGenerate)
setupPublishing()

nmcp {
    publishAllPublications {}
}
