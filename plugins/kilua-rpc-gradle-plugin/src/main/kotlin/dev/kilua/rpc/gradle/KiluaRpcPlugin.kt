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
import io.vertx.gradle.VertxExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun
import org.tomlj.Toml
import java.util.Locale.US

public enum class RpcServerType {
    Javalin, Jooby, Ktor, Micronaut, SpringBoot, VertX
}

public abstract class KiluaRpcPlugin() : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        logger.debug("Applying Kilua RPC plugin")

        val kiluaRpcExtension = createKiluaRpcExtension()

        val versions =
            Toml.parse(this@KiluaRpcPlugin.javaClass.classLoader.getResourceAsStream("dev.kilua.rpc.versions.toml"))
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
        return extensions.create("kilua-rpc", KiluaRpcExtension::class)
    }

    private data class KiluaRpcPluginContext(
        private val project: Project,
        val kiluaRpcExtension: KiluaRpcExtension,
        val kiluaRpcVersion: String
    ) : Project by project

    private fun KiluaRpcPluginContext.configureProject() {
        logger.debug("Configuring Kotlin/MPP plugin")

        val jvmMainExists = layout.projectDirectory.dir("src/jvmMain").asFile.exists()
        val jsMainExists = layout.projectDirectory.dir("src/jsMain").asFile.exists()
        val wasmJsMainExists = layout.projectDirectory.dir("src/wasmJsMain").asFile.exists()
        val webMainExists = jsMainExists || wasmJsMainExists

        if (webMainExists && jvmMainExists && kiluaRpcExtension.enableKsp.get()) {
            if (!pluginManager.hasPlugin("java")) {
                pluginManager.apply("java")
            }
            pluginManager.apply("com.google.devtools.ksp")
        }

        val kotlinMppExtension = extensions.getByType<KotlinMultiplatformExtension>()

        if (webMainExists && jvmMainExists) {
            afterEvaluate {
                kotlinMppExtension.targets.configureEach {
                    compilations.configureEach {
                        compileTaskProvider.configure {
                            compilerOptions {
                                freeCompilerArgs.add("-Xexpect-actual-classes")
                            }
                        }
                    }
                }
            }
            if (kiluaRpcExtension.enableKsp.get()) {
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

                afterEvaluate {
                    kotlinMppExtension.sourceSets.getByName("commonMain").kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
                    if (jsMainExists) {
                        kotlinMppExtension.sourceSets.getByName("jsMain").kotlin.srcDir("build/generated/ksp/js/jsMain/kotlin")
                    }
                    if (wasmJsMainExists) {
                        kotlinMppExtension.sourceSets.getByName("wasmJsMain").kotlin.srcDir("build/generated/ksp/wasmJs/wasmJsMain/kotlin")
                    }
                    kotlinMppExtension.sourceSets.getByName("jvmMain").kotlin.srcDir("build/generated/ksp/jvm/jvmMain/kotlin")

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

                tasks.all.kspKotlinJvm.configureEach {
                    dependsOn("kspCommonMainKotlinMetadata")
                }

                if (kiluaRpcExtension.enableGradleTasks.get()) {
                    tasks.create("generateKiluaRpcSources") {
                        group = KILUA_RPC_TASK_GROUP
                        description = "Generates Kilua RPC sources"
                        dependsOn("kspCommonMainKotlinMetadata")
                    }
                }
            }
            if (kiluaRpcExtension.enableGradleTasks.get()) {
                afterEvaluate {
                    afterEvaluate {
                        val serverType = getServerType(project)
                        val assetsPath = when (serverType) {
                            RpcServerType.Micronaut, RpcServerType.SpringBoot -> "/public"
                            RpcServerType.VertX -> "/webroot"
                            else -> "/assets"
                        }
                        if (jsMainExists) {
                            createWebArchiveTask("js", "js", assetsPath)
                        }
                        if (wasmJsMainExists) {
                            createWebArchiveTask("wasmJs", "wasm-js", assetsPath)
                        }
                        tasks.getByName("jvmProcessResources", Copy::class) {
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        }
                        when (serverType) {
                            RpcServerType.Javalin, RpcServerType.Jooby, RpcServerType.Ktor -> {
                                if (jsMainExists) {
                                    createShadowJarTask("jarWithJs", "js")
                                }
                                if (wasmJsMainExists) {
                                    createShadowJarTask("jarWithWasmJs", "wasmJs")
                                }
                                val defaultJarTaskName = if (jsMainExists) "jarWithJs" else "jarWithWasmJs"
                                tasks.findByName("jar")?.apply {
                                    enabled = false
                                    dependsOn(defaultJarTaskName)
                                }
                            }

                            RpcServerType.SpringBoot -> {
                                if (jsMainExists) {
                                    createBootJarTask("jarWithJs", "js", kotlinMppExtension)
                                }
                                if (wasmJsMainExists) {
                                    createBootJarTask("jarWithWasmJs", "wasmJs", kotlinMppExtension)
                                }
                                val defaultJarTaskName = if (jsMainExists) "jarWithJs" else "jarWithWasmJs"
                                tasks.findByName("jar")?.apply {
                                    enabled = false
                                    dependsOn(defaultJarTaskName)
                                }
                                tasks.getByName("bootRun", BootRun::class) {
                                    dependsOn("jvmMainClasses")
                                    classpath = files(
                                        kotlinMppExtension.targets["jvm"].compilations["main"].output.allOutputs,
                                        project.configurations["jvmRuntimeClasspath"]
                                    )
                                }
                                tasks.getByName("jvmRun").apply {
                                    dependsOn("bootRun")
                                }
                            }

                            RpcServerType.Micronaut -> {
                                if (jsMainExists) {
                                    createShadowJarTask("jarWithJs", "js")
                                }
                                if (wasmJsMainExists) {
                                    createShadowJarTask("jarWithWasmJs", "wasmJs")
                                }
                                val defaultJarTaskName = if (jsMainExists) "jarWithJs" else "jarWithWasmJs"
                                tasks.findByName("jar")?.apply {
                                    enabled = false
                                    dependsOn(defaultJarTaskName)
                                }
                                if (kiluaRpcExtension.enableKsp.get()) {
                                    tasks.getByName("kaptGenerateStubsKotlinJvm").apply {
                                        dependsOn("kspCommonMainKotlinMetadata")
                                    }
                                }
                                tasks.getByName("jvmRun").apply {
                                    dependsOn("run")
                                }
                            }

                            RpcServerType.VertX -> {
                                afterEvaluate {
                                    val vertxExtension = extensions.getByType<VertxExtension>()
                                    if (jsMainExists) {
                                        createShadowJarTask(
                                            "jarWithJs",
                                            "js",
                                            mapOf("Main-Verticle" to vertxExtension.mainVerticle)
                                        )
                                    }
                                    if (wasmJsMainExists) {
                                        createShadowJarTask(
                                            "jarWithWasmJs",
                                            "wasmJs",
                                            mapOf("Main-Verticle" to vertxExtension.mainVerticle)
                                        )
                                    }
                                    val defaultJarTaskName = if (jsMainExists) "jarWithJs" else "jarWithWasmJs"
                                    tasks.findByName("jar")?.apply {
                                        enabled = false
                                        dependsOn(defaultJarTaskName)
                                    }
                                }
                                tasks.getByName("jvmRun").apply {
                                    dependsOn("vertxRun")
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun KiluaRpcPluginContext.createWebArchiveTask(prefix: String, appendix: String, assetsPath: String) {
        tasks.create("${prefix}Archive", Jar::class).apply {
            dependsOn("${prefix}BrowserDistribution")
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a jar archive containing $prefix web application."
            archiveAppendix.set(appendix)
            val distribution =
                project.tasks.getByName(
                    "${prefix}BrowserDistribution",
                    Copy::class
                ).outputs
            from(distribution) {
                include("*.*")
                include("composeResources/**")
            }
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into(assetsPath)
            inputs.files(distribution)
            outputs.file(archiveFile)
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to rootProject.name,
                        "Implementation-Group" to rootProject.group,
                        "Implementation-Version" to rootProject.version,
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
        tasks.create(name, ShadowJar::class).apply {
            dependsOn("${webPrefix}Archive", "jvmJar")
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to rootProject.name,
                        "Implementation-Group" to rootProject.group,
                        "Implementation-Version" to rootProject.version,
                        "Timestamp" to System.currentTimeMillis(),
                        "Main-Class" to tasks.getByName(
                            "jvmRun",
                            JavaExec::class
                        ).mainClass.get()
                    ) + manifestAttributes
                )
            }
            configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
            from(project.tasks["${webPrefix}Archive"].outputs.files, project.tasks["jvmJar"].outputs.files)
            outputs.file(archiveFile)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            mergeServiceFiles()
        }
    }

    private fun KiluaRpcPluginContext.createBootJarTask(
        name: String,
        webPrefix: String,
        kotlinMppExtension: KotlinMultiplatformExtension
    ) {
        tasks.create(name, BootJar::class) {
            dependsOn("${webPrefix}Archive")
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            mainClass.set(tasks.getByName("bootJar", BootJar::class).mainClass)
            targetJavaVersion.set(tasks.getByName("bootJar", BootJar::class).targetJavaVersion)
            classpath = files(
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
        val rpcServerDependency = project.configurations["commonMainImplementation"].dependencies.map {
            it.name
        }.firstOrNull { it.startsWith("kilua-rpc-") }
        if (rpcServerDependency != null) {
            return when (rpcServerDependency) {
                "kilua-rpc-javalin" -> RpcServerType.Javalin
                "kilua-rpc-jooby" -> RpcServerType.Jooby
                "kilua-rpc-ktor-guice" -> RpcServerType.Ktor
                "kilua-rpc-ktor-koin" -> RpcServerType.Ktor
                "kilua-rpc-micronaut" -> RpcServerType.Micronaut
                "kilua-rpc-spring-boot" -> RpcServerType.SpringBoot
                "kilua-rpc-vertx" -> RpcServerType.VertX
                else -> return null
            }
        } else {
            // Enable packaging tasks for other use cases without kilua-rpc dependency
            val jvmMainDependencies = project.configurations["jvmMainImplementation"].dependencies.map { it.name }
            if (jvmMainDependencies.contains("spring-boot-starter-web")) {
                return RpcServerType.SpringBoot
            }
            val kiluaSsrDependency = jvmMainDependencies.firstOrNull { it.startsWith("kilua-ssr-server-") }
            return when (kiluaSsrDependency) {
                "kilua-ssr-server-javalin" -> RpcServerType.Javalin
                "kilua-ssr-server-jooby" -> RpcServerType.Jooby
                "kilua-ssr-server-ktor" -> RpcServerType.Ktor
                "kilua-ssr-server-micronaut" -> RpcServerType.Micronaut
                "kilua-ssr-server-spring-boot" -> RpcServerType.SpringBoot
                "kilua-ssr-server-vertx" -> RpcServerType.VertX
                else -> return null
            }
        }
    }

    public companion object {

        public const val KILUA_RPC_TASK_GROUP: String = "Kilua RPC"
        public const val PACKAGE_TASK_GROUP: String = "package"

    }

}
