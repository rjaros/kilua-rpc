plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    id("com.android.kotlin.multiplatform.library")
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
        getByName("jvmAndAndroidMain") {
            dependencies {
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.websockets)
                api(libs.ktor.client.cio)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.coroutines)
                // The logger used by the agent is part of the ABI through the @PublishedApi member
                // of RpcAgent, so it has to stay on the compilation classpath of consumers. It also
                // brings slf4j-api along, which is what the JVM default logger writes through - the
                // library still ships no binding, consumers supply their own.
                api(libs.ktor.client.logging)
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
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit.junit4)
            }
        }
    }
}

setupDokka(tasks.dokkaGeneratePublicationHtml)
setupPublishing()
