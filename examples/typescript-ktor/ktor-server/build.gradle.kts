import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kilua.rpc)
}

val mainClassName = "io.ktor.server.netty.EngineMain"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(mainClassName)
        }
    }
    js(IR) {
        useEsModules()
        browser {
            @OptIn(ExperimentalDistributionDsl::class)
            distribution {
                val mainProject = project.parent!!
                val mainDir = mainProject.layout.projectDirectory
                val generatedDir = "generated-packages/${project.name}"
                outputDirectory = mainDir.dir(generatedDir)
            }
        }
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target.set("es2015")
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kilua.rpc.ktor)
                implementation(libs.kotlinx.datetime)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.compression)
                implementation(libs.logback.classic)
            }
        }
    }
}
