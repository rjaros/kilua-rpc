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
                api(project(":modules:kilua-rpc-core"))
                api(project(":modules:kilua-rpc-annotations"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(kotlin("reflect"))
                api(libs.kotlinx.coroutines.jdk8)
                api(project.dependencies.platform(libs.quarkus.bom))
                api(libs.quarkus.rest)
                api(libs.quarkus.vertx.http)
                implementation(libs.quarkus.vertx.lang.kotlin.coroutines)
                implementation(libs.quarkus.arc)
            }
        }
    }
}

setupDokka(tasks.dokkaGeneratePublicationHtml)
setupPublishing()
