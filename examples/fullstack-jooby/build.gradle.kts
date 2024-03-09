import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.jooby)
    alias(libs.plugins.kilua.rpc)
}

val mainClassNameVal = "example.MainKt"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(17)
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xjsr305=strict")
            }
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set(mainClassNameVal)
        }
    }
    js(IR) {
        // useEsModules() workaround modules order (https://youtrack.jetbrains.com/issue/KT-64616)
        browser {
            commonWebpackConfig {
                outputFileName = "main.bundle.js"
            }
            runTask {
                sourceMaps = false
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }
    wasmJs {
        useEsModules()
        browser {
            commonWebpackConfig {
                outputFileName = "main.bundle.js"
            }
            runTask {
                sourceMaps = false
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
        if (project.gradle.startParameter.taskNames.find {
                it.contains("wasmJsBrowserProductionWebpack") || it.contains("wasmJsArchive") || it.contains("jarWithWasmJs")
            } != null) {
            applyBinaryen {
                binaryenArgs = mutableListOf(
                    "--enable-nontrapping-float-to-int",
                    "--enable-gc",
                    "--enable-reference-types",
                    "--enable-exception-handling",
                    "--enable-bulk-memory",
                    "--inline-functions-with-loops",
                    "--traps-never-happen",
                    "--fast-math",
                    "--closed-world",
                    "--metrics",
                    "-O3", "--gufa", "--metrics",
                    "-O3", "--gufa", "--metrics",
                    "-O3", "--gufa", "--metrics",
                )
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kilua.rpc.jooby)
                api(libs.kotlinx.datetime)
            }
        }
        val webMain by creating {
            dependsOn(commonMain)
        }
        val jsMain by getting {
            dependsOn(webMain)
        }
        val wasmJsMain by getting {
            dependsOn(webMain)
        }
        val jvmMain by getting {
            dependencies {
                api(libs.jooby.netty)
                implementation(libs.logback.classic)
            }
        }
    }
}

tasks {
    joobyRun {
        mainClass = mainClassNameVal
        restartExtensions = listOf("conf", "properties", "class")
        compileExtensions = listOf("java", "kt")
        port = 8080
    }
}
