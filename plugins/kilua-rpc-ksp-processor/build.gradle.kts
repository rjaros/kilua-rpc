plugins {
    kotlin("multiplatform")
    alias(libs.plugins.test.balloon)
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
}

kotlin {
    explicitApi()
    compilerOptions()
    kotlinJvmTargets("21")
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
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.test.balloon)
            }
        }
    }
}

setupDokka("plugins/")
setupPublishing()
