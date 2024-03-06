package dev.kilua.rpc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.tomlj.Toml

public abstract class KiluaRpcPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        logger.debug("Applying Kilua RPC plugin")
        val versions =
            Toml.parse(this@KiluaRpcPlugin.javaClass.classLoader.getResourceAsStream("dev.kilua.rpc.versions.toml"))
        val kiluaRpcVersion = versions.getString("versions.kilua-rpc") ?: "undefined"
        with(KiluaRpcPluginContext(project, kiluaRpcVersion)) {
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                configureProject()
            }
        }
    }

    private data class KiluaRpcPluginContext(
        private val project: Project,
        val kiluaRpcVersion: String
    ) : Project by project

    private fun KiluaRpcPluginContext.configureProject() {
        logger.debug("Configuring Kotlin/MPP plugin")
        val jvmMainExists = layout.projectDirectory.dir("src/jvmMain").asFile.exists()
        val jsMainExists = layout.projectDirectory.dir("src/jsMain").asFile.exists()
        val wasmJsMainExists = layout.projectDirectory.dir("src/wasmJsMain").asFile.exists()
        if ((jsMainExists || wasmJsMainExists) && jvmMainExists) {
            if (!plugins.hasPlugin("java")) {
                plugins.apply("java")
            }
            plugins.apply("com.google.devtools.ksp")
        }

        val kotlinMppExtension = extensions.getByType<KotlinMultiplatformExtension>()

        if ((jsMainExists || wasmJsMainExists) && jvmMainExists) {
            afterEvaluate {
                kotlinMppExtension.targets.configureEach {
                    compilations.configureEach {
                        compilerOptions.configure {
                            freeCompilerArgs.add("-Xexpect-actual-classes")
                        }
                    }
                }
            }
            if (jsMainExists) {
                tasks.all.compileKotlinJs.configureEach {
                    dependsOn("kspCommonMainKotlinMetadata")
                }
            }
            if (wasmJsMainExists) {
                tasks.all.compileKotlinWasmJs.configureEach {
                    dependsOn("kspCommonMainKotlinMetadata")
                }
            }

            tasks.all.compileKotlinJvm.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }

            dependencies {
                add("kspCommonMainMetadata", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}")
            }

            afterEvaluate {
                dependencies {
                    if (jsMainExists) {
                        add("kspJs", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}")
                    }
                    if (wasmJsMainExists) {
                        add("kspWasmJs", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}")
                    }
                }
                kotlinMppExtension.sourceSets.getByName("commonMain").kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
                if (jsMainExists) {
                    kotlinMppExtension.sourceSets.getByName("jsMain").kotlin.srcDir("build/generated/ksp/js/jsMain/kotlin")
                }
                if (wasmJsMainExists) {
                    kotlinMppExtension.sourceSets.getByName("wasmJsMain").kotlin.srcDir("build/generated/ksp/wasmJs/wasmJsMain/kotlin")
                }

                // Workaround duplicated source roots in IntelliJ IDEA
                afterEvaluate {
                    afterEvaluate {
                        afterEvaluate {
                            afterEvaluate {
                                kotlinMppExtension.sourceSets.filter { it.name.startsWith("generatedByKsp") }
                                    .forEach {
                                        kotlinMppExtension.sourceSets.remove(it)
                                    }
                            }
                        }
                    }
                }
            }

            tasks.all.kspKotlinJs.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }

            tasks.all.kspKotlinWasmJs.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }

            tasks.create("generateKiluaRpcSources") {
                group = KILUA_RPC_TASK_GROUP
                description = "Generates Kilua RPC sources"
                dependsOn("kspCommonMainKotlinMetadata")
            }
        }
    }

    private val TaskContainer.all: TaskCollections get() = TaskCollections(this)

    /** Lazy task collections */
    private inner class TaskCollections(private val tasks: TaskContainer) {

        val compileKotlinJs: TaskCollection<KotlinCompile<*>>
            get() = collection("compileKotlinJs")

        val compileKotlinWasmJs: TaskCollection<KotlinCompile<*>>
            get() = collection("compileKotlinWasmJs")

        val compileKotlinJvm: TaskCollection<KotlinCompile<*>>
            get() = collection("compileKotlinJvm")

        val kspKotlinJs: TaskCollection<Task>
            get() = collection("kspKotlinJs")

        val kspKotlinWasmJs: TaskCollection<Task>
            get() = collection("kspKotlinWasmJs")

        private inline fun <reified T : Task> collection(taskName: String): TaskCollection<T> =
            tasks.withType<T>().matching { it.name == taskName }
    }

    public companion object {

        public const val KILUA_RPC_TASK_GROUP: String = "kilua-rpc"
        public const val PACKAGE_TASK_GROUP: String = "package"

    }

}
