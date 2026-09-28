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
        getByName("jvmMain") {
            dependencies {
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.websockets)
                api(libs.ktor.client.cio)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.coroutines)
                // Logger leaks into the ABI through the @PublishedApi member of RpcAgent,
                // so it has to stay on the compile classpath of consumers. The library ships no
                // binding - consumers supply their own (all of the server modules and examples
                // already pull in logback).
                api(libs.slf4j.api)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        getByName("jvmTest") {
            dependencies {
                // A binding is only needed to make the agent's logging observable in tests.
                runtimeOnly(libs.logback.classic)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.sse)
            }
        }
    }
}

setupDokka(tasks.dokkaGeneratePublicationHtml)
setupPublishing()
