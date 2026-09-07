import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.allopen") version libs.versions.kotlin.get()
    id("com.google.devtools.ksp")
    alias(libs.plugins.kilua.rpc)
}

extra["mainClassName"] = "example.MainKt"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(25)
    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(project.extra["mainClassName"].toString())
        }
    }
    js {
        useEsModules()
        browser {
            commonWebpackConfig {
                outputFileName = "main.bundle.js"
            }
        }
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
    }
    wasmJs {
        useEsModules()
        browser {
            commonWebpackConfig {
                outputFileName = "main.bundle.js"
            }
        }
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
    }
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.kilua.rpc.quarkus)
                implementation(libs.kotlinx.datetime)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(kotlin("reflect"))
                implementation(project.dependencies.platform(libs.quarkus.bom))
                implementation(libs.quarkus.rest)
                implementation(libs.quarkus.kotlin)
                implementation(libs.quarkus.rest.kotlin.serialization)
                implementation(libs.quarkus.vertx.http)
                implementation(libs.quarkus.arc)
                implementation(libs.quarkus.config.yaml)
            }
        }
    }
}
