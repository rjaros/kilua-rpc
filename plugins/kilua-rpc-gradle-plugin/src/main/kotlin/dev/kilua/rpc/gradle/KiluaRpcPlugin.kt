/*
 * Copyright (c) 2024 Robert Jaros
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.kilua.rpc.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.extensions.core.extra
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.tomlj.Toml
import java.util.Locale.US

public enum class RpcServerType {
    Javalin, Jooby, Ktor, Micronaut, SpringBoot, VertX
}

public abstract class KiluaRpcPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        logger.debug("Applying Kilua RPC plugin")

        val kiluaRpcExtension = createKiluaRpcExtension()

        val versions =
            Toml.parse(this@KiluaRpcPlugin.javaClass.classLoader.getResourceAsStream("dev.kilua.rpc.versions.toml")!!)
        val kiluaRpcVersion = versions.getString("versions.kilua-rpc") ?: "undefined"
        with(KiluaRpcPluginContext(project, kiluaRpcExtension, kiluaRpcVersion)) {
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                configureProject()
            }
        }
    }

    /**
     * Initialise the [KiluaRpcExtension] on a [Project].
     */
    private fun Project.createKiluaRpcExtension(): KiluaRpcExtension {
        return extensions.create<KiluaRpcExtension>("kilua-rpc")
    }

    private data class KiluaRpcPluginContext(
        val project: Project,
        val kiluaRpcExtension: KiluaRpcExtension,
        val kiluaRpcVersion: String
    )

    private fun KiluaRpcPluginContext.configureProject() {
        project.logger.debug("Configuring Kotlin/MPP plugin")

        val enableKsp = project.pluginManager.hasPlugin("com.google.devtools.ksp")

        val kotlinMppExtension = project.extensions.getByType<KotlinMultiplatformExtension>()

        kotlinMppExtension.targets.configureEach {
            val targetName = name
            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.add("-Xexpect-actual-classes")
                        optIn.add("kotlin.time.ExperimentalTime")
                        if (targetName == "metadata" || targetName == "js" || targetName == "wasmJs") {
                            optIn.add("kotlin.js.ExperimentalWasmJsInterop")
                        }
                    }
                }
            }
        }

        if (enableKsp) {
            project.tasks.all.compileKotlinJs.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.compileKotlinWasmJs.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.compileKotlinJvm.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }
            project.dependencies {
                add("kspCommonMainMetadata", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}")
            }
            kotlinMppExtension.targets
                .matching {
                    it.platformType in setOf(
                        KotlinPlatformType.jvm,
                        KotlinPlatformType.js,
                        KotlinPlatformType.wasm
                    )
                }.configureEach {
                    project.dependencies.add(
                        "ksp${
                            targetName.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(
                                    US
                                ) else it.toString()
                            }
                        }", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}"
                    )
                }
            kotlinMppExtension.sourceSets.configureEach {
                when (name) {
                    "commonMain" -> kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
                    "jsMain" -> kotlin.srcDir("build/generated/ksp/js/jsMain/kotlin")
                    "wasmJsMain" -> kotlin.srcDir("build/generated/ksp/wasmJs/wasmJsMain/kotlin")
                    "jvmMain" -> kotlin.srcDir("build/generated/ksp/jvm/jvmMain/kotlin")
                }
            }
            project.tasks.all.kspKotlinJs.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.kspKotlinWasmJs.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.kspKotlinJvm.configureEach {
                dependsOn("kspCommonMainKotlinMetadata")
            }

            if (kiluaRpcExtension.enableGradleTasks.get()) {
                project.tasks.register("generateKiluaRpcSources") {
                    group = KILUA_RPC_TASK_GROUP
                    description = "Generates Kilua RPC sources"
                    dependsOn("kspCommonMainKotlinMetadata")
                }
            }
        }

        if (kiluaRpcExtension.enableGradleTasks.get()) {
            project.afterEvaluate {
                afterEvaluate {
                    val serverType = getServerType(project)
                    val assetsPath = when (serverType) {
                        RpcServerType.Micronaut, RpcServerType.SpringBoot -> "/public"
                        RpcServerType.VertX -> "/webroot"
                        else -> "/assets"
                    }
                    val isJsTarget = kotlinMppExtension.targets.any { it.platformType == KotlinPlatformType.js }
                    val isWasmJsTarget = kotlinMppExtension.targets.any { it.platformType == KotlinPlatformType.wasm }
                    if (isJsTarget) createWebArchiveTask("js", "js", assetsPath)
                    if (isWasmJsTarget) createWebArchiveTask("wasmJs", "wasm-js", assetsPath)
                    tasks.getByName<Copy>("jvmProcessResources") {
                        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    }
                    when (serverType) {
                        RpcServerType.Javalin, RpcServerType.Jooby, RpcServerType.Ktor -> {
                            if (isJsTarget) createShadowJarTask("jarWithJs", "js")
                            if (isWasmJsTarget) createShadowJarTask("jarWithWasmJs", "wasmJs")
                            tasks.findByName("jar")?.enabled = false
                        }

                        RpcServerType.SpringBoot -> {
                            val mainClassName = extra["mainClassName"].toString()
                            if (isJsTarget)
                                createBootJarTask("jarWithJs", "js", mainClassName, kotlinMppExtension)
                            if (isWasmJsTarget)
                                createBootJarTask("jarWithWasmJs", "wasmJs", mainClassName, kotlinMppExtension)
                            tasks.findByName("jar")?.enabled = false
                            tasks.getByName("jvmRun").apply {
                                subprojects.forEach {
                                    if (it.name == "application") {
                                        dependsOn("${it.path}:bootRun")
                                    }
                                }
                            }
                        }

                        RpcServerType.Micronaut -> {
                            if (isJsTarget) createShadowJarTask("jarWithJs", "js")
                            if (isWasmJsTarget) createShadowJarTask("jarWithWasmJs", "wasmJs")
                            tasks.findByName("jar")?.enabled = false
                            tasks.getByName("jvmRun").apply {
                                subprojects.forEach {
                                    if (it.name == "application") {
                                        dependsOn("${it.path}:run")
                                    }
                                }
                            }
                        }

                        RpcServerType.VertX -> {
                            val mainClassName = extra["mainClassName"].toString()
                            if (isJsTarget)
                                createShadowJarTask("jarWithJs", "js", mapOf("Main-Verticle" to mainClassName))
                            if (isWasmJsTarget)
                                createShadowJarTask("jarWithWasmJs", "wasmJs", mapOf("Main-Verticle" to mainClassName))
                            tasks.findByName("jar")?.enabled = false
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun KiluaRpcPluginContext.createWebArchiveTask(prefix: String, appendix: String, assetsPath: String) {
        project.tasks.register<Jar>("${prefix}Archive") {
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a jar archive containing $prefix web application."
            archiveAppendix.set(appendix)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into(assetsPath)
            outputs.file(archiveFile)
            project.tasks.findByName("${prefix}BrowserDistribution")?.let {
                dependsOn(it)
                val distribution = it.outputs
                from(distribution)
                inputs.files(distribution)
            }
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to project.rootProject.name,
                        "Implementation-Group" to project.rootProject.group,
                        "Implementation-Version" to project.rootProject.version,
                        "Timestamp" to System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun KiluaRpcPluginContext.createShadowJarTask(
        name: String,
        webPrefix: String,
        manifestAttributes: Map<String, String> = emptyMap()
    ) {
        project.tasks.register<ShadowJar>(name) {
            dependsOn("${webPrefix}Archive", "jvmJar")
            kiluaRpcExtension.jarArchiveFileName.orNull?.let {
                archiveFileName.set(kiluaRpcExtension.jarArchiveFileName)
            }
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to project.rootProject.name,
                        "Implementation-Group" to project.rootProject.group,
                        "Implementation-Version" to project.rootProject.version,
                        "Timestamp" to System.currentTimeMillis(),
                        "Main-Class" to project.tasks.getByName<JavaExec>("jvmRun").mainClass.get()
                    ) + manifestAttributes
                )
            }
            configurations.convention(listOf(project.configurations.getByName("jvmRuntimeClasspath")))
            includedDependencies.from(
                project.tasks["${webPrefix}Archive"].outputs.files,
                project.tasks["jvmJar"].outputs.files
            )
            outputs.file(archiveFile)
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            mergeServiceFiles()
            append("META-INF/http/mime.types")
        }
    }

    private fun KiluaRpcPluginContext.createBootJarTask(
        name: String,
        webPrefix: String,
        mainClassName: String,
        kotlinMppExtension: KotlinMultiplatformExtension
    ) {
        project.tasks.register<BootJar>(name) {
            dependsOn("${webPrefix}Archive")
            kiluaRpcExtension.jarArchiveFileName.orNull?.let {
                archiveFileName.set(kiluaRpcExtension.jarArchiveFileName)
            }
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            mainClass.set(mainClassName)
            targetJavaVersion.set(JavaVersion.VERSION_21)
            classpath = project.files(
                kotlinMppExtension.targets["jvm"].compilations["main"].output.allOutputs,
                project.configurations["jvmRuntimeClasspath"],
                (project.tasks["${webPrefix}Archive"] as Jar).archiveFile
            )
        }
    }

    private val TaskContainer.all: TaskCollections get() = TaskCollections(this)

    /** Lazy task collections */
    private inner class TaskCollections(private val tasks: TaskContainer) {

        val compileKotlinJs: TaskCollection<KotlinCompilationTask<*>>
            get() = collection("compileKotlinJs")

        val compileKotlinWasmJs: TaskCollection<KotlinCompilationTask<*>>
            get() = collection("compileKotlinWasmJs")

        val compileKotlinJvm: TaskCollection<KotlinCompilationTask<*>>
            get() = collection("compileKotlinJvm")

        val kspKotlinJs: TaskCollection<Task>
            get() = collection("kspKotlinJs")

        val kspKotlinWasmJs: TaskCollection<Task>
            get() = collection("kspKotlinWasmJs")

        val kspKotlinJvm: TaskCollection<Task>
            get() = collection("kspKotlinJvm")

        private inline fun <reified T : Task> collection(taskName: String): TaskCollection<T> =
            tasks.withType<T>().matching { it.name == taskName }
    }

    private fun getServerType(project: Project): RpcServerType? {
        val commonMainDependencies = project.configurations["commonMainImplementation"].dependencies.map { it.name }
        val kiluaRpcDependency = commonMainDependencies.firstOrNull { it.startsWith("kilua-rpc-") }
        when (kiluaRpcDependency) {
            "kilua-rpc-javalin", "kilua-rpc-javalin-koin" -> return RpcServerType.Javalin
            "kilua-rpc-jooby", "kilua-rpc-jooby-koin" -> return RpcServerType.Jooby
            "kilua-rpc-ktor", "kilua-rpc-ktor-koin" -> return RpcServerType.Ktor
            "kilua-rpc-micronaut" -> return RpcServerType.Micronaut
            "kilua-rpc-spring-boot" -> return RpcServerType.SpringBoot
            "kilua-rpc-vertx", "kilua-rpc-vertx-koin" -> return RpcServerType.VertX
        }
        val jvmMainDependencies = project.configurations["jvmMainImplementation"].dependencies.map { it.name }
        val kiluaSsrDependency = jvmMainDependencies.firstOrNull { it.startsWith("kilua-ssr-server-") }
        when (kiluaSsrDependency) {
            "kilua-ssr-server-javalin" -> return RpcServerType.Javalin
            "kilua-ssr-server-jooby" -> return RpcServerType.Jooby
            "kilua-ssr-server-ktor" -> return RpcServerType.Ktor
            "kilua-ssr-server-micronaut" -> return RpcServerType.Micronaut
            "kilua-ssr-server-spring-boot" -> return RpcServerType.SpringBoot
            "kilua-ssr-server-vertx" -> return RpcServerType.VertX
        }
        if (jvmMainDependencies.contains("javalin")) {
            return RpcServerType.Javalin
        }
        if (jvmMainDependencies.contains("jooby-kotlin")) {
            return RpcServerType.Jooby
        }
        if (jvmMainDependencies.contains("ktor-server-core") || jvmMainDependencies.contains("ktor-server-core-jvm")) {
            return RpcServerType.Ktor
        }
        if (jvmMainDependencies.contains("micronaut-runtime")) {
            return RpcServerType.Micronaut
        }
        if (jvmMainDependencies.contains("spring-boot-starter-web") || jvmMainDependencies.contains("spring-boot-starter-webflux")) {
            return RpcServerType.SpringBoot
        }
        if (jvmMainDependencies.contains("vertx-web")) {
            return RpcServerType.VertX
        }
        return null
    }

    public companion object {

        public const val KILUA_RPC_TASK_GROUP: String = "Kilua RPC"
        public const val PACKAGE_TASK_GROUP: String = "package"

    }
}
