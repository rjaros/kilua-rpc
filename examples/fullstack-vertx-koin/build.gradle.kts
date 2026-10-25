import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kilua.rpc)
}

extra["mainClassName"] = "example.MainVerticle"

kotlin {
    jvmToolchain(25)
    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set("io.vertx.launcher.application.VertxApplication")
            args(project.extra["mainClassName"]!!)
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
                implementation(project(":modules:kilua-rpc-vertx-koin"))
                implementation(libs.kotlinx.datetime)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}
