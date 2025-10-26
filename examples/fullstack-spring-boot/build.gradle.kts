import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    kotlin("plugin.spring") version libs.versions.kotlin.get()
    alias(libs.plugins.kilua.rpc)
}

extra["mainClassName"] = "example.MainKt"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
    js(IR) {
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
        val commonMain by getting {
            dependencies {
                implementation(libs.kilua.rpc.spring.boot)
                implementation(libs.kotlinx.datetime)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("reflect"))
                implementation(project.dependencies.platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
                implementation("org.springframework.boot:spring-boot-starter")
                implementation("org.springframework.boot:spring-boot-starter-webflux")
                implementation("org.springframework.boot:spring-boot-starter-security")
            }
        }
    }
}
