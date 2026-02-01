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
     * Initialize the [KiluaRpcExtension] on a [Project].
     */
    private fun Project.createKiluaRpcExtension(): KiluaRpcExtension {
        return extensions.create("kilua-rpc", KiluaRpcExtension::class.java)
    }

    private data class KiluaRpcPluginContext(
        val project: Project,
        val kiluaRpcExtension: KiluaRpcExtension,
        val kiluaRpcVersion: String
    )

    private fun KiluaRpcPluginContext.configureProject() {
        project.logger.debug("Configuring Kotlin/MPP plugin")

        val enableKsp = project.pluginManager.hasPlugin("com.google.devtools.ksp")

        val kotlinMppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

        kotlinMppExtension.targets.configureEach { target ->
            val targetName = target.name
            target.compilations.configureEach { compilation ->
                compilation.compileTaskProvider.configure {
                    it.compilerOptions {
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
                it.dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.compileKotlinWasmJs.configureEach {
                it.dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.compileKotlinJvm.configureEach {
                it.dependsOn("kspCommonMainKotlinMetadata")
            }
            project.dependencies.add("kspCommonMainMetadata", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}")
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
                            it.targetName.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(
                                    US
                                ) else it.toString()
                            }
                        }", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}"
                    )
                }
            kotlinMppExtension.sourceSets.configureEach {
                when (it.name) {
                    "commonMain" -> it.kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
                    "jsMain" -> it.kotlin.srcDir("build/generated/ksp/js/jsMain/kotlin")
                    "wasmJsMain" -> it.kotlin.srcDir("build/generated/ksp/wasmJs/wasmJsMain/kotlin")
                    "jvmMain" -> it.kotlin.srcDir("build/generated/ksp/jvm/jvmMain/kotlin")
                }
            }
            project.tasks.all.kspKotlinJs.configureEach {
                it.dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.kspKotlinWasmJs.configureEach {
                it.dependsOn("kspCommonMainKotlinMetadata")
            }
            project.tasks.all.kspKotlinJvm.configureEach {
                it.dependsOn("kspCommonMainKotlinMetadata")
            }

            if (kiluaRpcExtension.enableGradleTasks.get()) {
                project.tasks.register("generateKiluaRpcSources") {
                    it.group = KILUA_RPC_TASK_GROUP
                    it.description = "Generates Kilua RPC sources"
                    it.dependsOn("kspCommonMainKotlinMetadata")
                }
            }
        }

        if (kiluaRpcExtension.enableGradleTasks.get()) {
            project.afterEvaluate {
                it.afterEvaluate {
                    val serverType = getServerType(project)
                    val assetsPath = when (serverType) {
                        RpcServerType.Micronaut, RpcServerType.SpringBoot -> "/public"
                        RpcServerType.VertX -> "/webroot"
                        else -> "/assets"
                    }
                    val isJsTarget = kotlinMppExtension.targets.any { it.platformType == KotlinPlatformType.js }
                    val isWasmJsTarget = kotlinMppExtension.targets.any { it.platformType == KotlinPlatformType.wasm }
                    if (isJsTarget) createWebArchiveTask("js", "js", assetsPath, false)
                    if (isWasmJsTarget) createWebArchiveTask("wasmJs", "wasm-js", assetsPath, isJsTarget)
                    (project.tasks.getByName("jvmProcessResources") as Copy).apply {
                        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    }
                    when (serverType) {
                        RpcServerType.Javalin, RpcServerType.Jooby, RpcServerType.Ktor -> {
                            if (isJsTarget) createShadowJarTask("jarWithJs", "js")
                            if (isWasmJsTarget) createShadowJarTask("jarWithWasmJs", "wasmJs")
                            it.tasks.findByName("jar")?.enabled = false
                        }

                        RpcServerType.SpringBoot -> {
                            val mainClassName = it.extra["mainClassName"].toString()
                            if (isJsTarget)
                                createBootJarTask("jarWithJs", "js", mainClassName, kotlinMppExtension)
                            if (isWasmJsTarget)
                                createBootJarTask("jarWithWasmJs", "wasmJs", mainClassName, kotlinMppExtension)
                            it.tasks.findByName("jar")?.enabled = false
                            it.tasks.getByName("jvmRun").apply {
                                it.subprojects.forEach {
                                    if (it.name == "application") {
                                        dependsOn("${it.path}:bootRun")
                                    }
                                }
                            }
                        }

                        RpcServerType.Micronaut -> {
                            if (isJsTarget) createShadowJarTask("jarWithJs", "js")
                            if (isWasmJsTarget) createShadowJarTask("jarWithWasmJs", "wasmJs")
                            it.tasks.findByName("jar")?.enabled = false
                            it.tasks.getByName("jvmRun").apply {
                                it.subprojects.forEach {
                                    if (it.name == "application") {
                                        dependsOn("${it.path}:run")
                                    }
                                }
                            }
                        }

                        RpcServerType.VertX -> {
                            val mainClassName = it.extra["mainClassName"].toString()
                            if (isJsTarget)
                                createShadowJarTask("jarWithJs", "js", mapOf("Main-Verticle" to mainClassName))
                            if (isWasmJsTarget)
                                createShadowJarTask("jarWithWasmJs", "wasmJs", mapOf("Main-Verticle" to mainClassName))
                            it.tasks.findByName("jar")?.enabled = false
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun KiluaRpcPluginContext.createWebArchiveTask(
        prefix: String,
        appendix: String,
        assetsPath: String,
        useCompat: Boolean
    ) {
        project.tasks.register("${prefix}Archive", Jar::class.java) { jar ->
            jar.group = KILUA_RPC_TASK_GROUP
            jar.description = "Assembles a jar archive containing $prefix web application."
            jar.archiveAppendix.set(appendix)
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.into(assetsPath)
            jar.outputs.file(jar.archiveFile)
            val distributionTaskName =
                if (useCompat && project.tasks.findByName("composeCompatibilityBrowserDistribution") != null) {
                    "composeCompatibilityBrowserDistribution"
                } else "${prefix}BrowserDistribution"
            project.tasks.findByName(distributionTaskName)?.let {
                jar.dependsOn(it)
                val distribution = it.outputs
                jar.from(distribution)
                jar.inputs.files(distribution)
            }
            jar.manifest {
                it.attributes(
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
        project.tasks.register(name, ShadowJar::class.java) { jar ->
            jar.dependsOn("${webPrefix}Archive", "jvmJar")
            kiluaRpcExtension.jarArchiveFileName.orNull?.let {
                jar.archiveFileName.set(kiluaRpcExtension.jarArchiveFileName)
            }
            jar.group = KILUA_RPC_TASK_GROUP
            jar.description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            jar.manifest {
                it.attributes(
                    mapOf(
                        "Implementation-Title" to project.rootProject.name,
                        "Implementation-Group" to project.rootProject.group,
                        "Implementation-Version" to project.rootProject.version,
                        "Timestamp" to System.currentTimeMillis(),
                        "Main-Class" to (project.tasks.getByName("jvmRun") as JavaExec).mainClass.get()
                    ) + manifestAttributes
                )
            }
            jar.configurations.convention(listOf(project.configurations.getByName("jvmRuntimeClasspath")))
            jar.includedDependencies.from(
                project.tasks.getByName("${webPrefix}Archive").outputs.files,
                project.tasks.getByName("jvmJar").outputs.files
            )
            jar.outputs.file(jar.archiveFile)
            jar.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            jar.mergeServiceFiles()
            jar.append("META-INF/http/mime.types")
        }
    }

    private fun KiluaRpcPluginContext.createBootJarTask(
        name: String,
        webPrefix: String,
        mainClassName: String,
        kotlinMppExtension: KotlinMultiplatformExtension
    ) {
        project.tasks.register(name, BootJar::class.java) { jar ->
            jar.dependsOn("${webPrefix}Archive")
            kiluaRpcExtension.jarArchiveFileName.orNull?.let {
                jar.archiveFileName.set(kiluaRpcExtension.jarArchiveFileName)
            }
            jar.group = KILUA_RPC_TASK_GROUP
            jar.description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            jar.mainClass.set(mainClassName)
            jar.targetJavaVersion.set(JavaVersion.VERSION_21)
            jar.setClasspath(
                project.files(
                    kotlinMppExtension.targets.getByName("jvm").compilations.getByName("main").output.allOutputs,
                    project.configurations.getByName("jvmRuntimeClasspath"),
                    (project.tasks.getByName("${webPrefix}Archive") as Jar).archiveFile
                )
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
            tasks.withType(T::class.java).matching { it.name == taskName }
    }

    private fun getServerType(project: Project): RpcServerType? {
        val commonMainDependencies =
            project.configurations.getByName("commonMainImplementation").dependencies.map { it.name }
        val kiluaRpcDependency = commonMainDependencies.firstOrNull { it.startsWith("kilua-rpc-") }
        when (kiluaRpcDependency) {
            "kilua-rpc-javalin", "kilua-rpc-javalin-koin" -> return RpcServerType.Javalin
            "kilua-rpc-jooby", "kilua-rpc-jooby-koin" -> return RpcServerType.Jooby
            "kilua-rpc-ktor", "kilua-rpc-ktor-koin" -> return RpcServerType.Ktor
            "kilua-rpc-micronaut" -> return RpcServerType.Micronaut
            "kilua-rpc-spring-boot" -> return RpcServerType.SpringBoot
            "kilua-rpc-vertx", "kilua-rpc-vertx-koin" -> return RpcServerType.VertX
        }
        val jvmMainDependencies = project.configurations.getByName("jvmMainImplementation").dependencies.map { it.name }
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
