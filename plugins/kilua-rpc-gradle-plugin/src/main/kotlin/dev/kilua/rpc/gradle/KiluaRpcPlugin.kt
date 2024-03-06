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
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun
import org.tomlj.Toml

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
            if (!plugins.hasPlugin("java")) {
                plugins.apply("java")
            }
            plugins.apply("com.google.devtools.ksp")
        }

        val kotlinMppExtension = extensions.getByType<KotlinMultiplatformExtension>()

        if (webMainExists && jvmMainExists) {
            afterEvaluate {
                kotlinMppExtension.targets.configureEach {
                    compilations.configureEach {
                        compilerOptions.configure {
                            freeCompilerArgs.add("-Xexpect-actual-classes")
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
                            createArchiveTask("js", "js", assetsPath)
                        }
                        if (wasmJsMainExists) {
                            createArchiveTask("wasmJs", "wasm-js", assetsPath)
                        }
                        tasks.getByName("jvmProcessResources", Copy::class) {
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        }
                        when (serverType) {
                            RpcServerType.Javalin, RpcServerType.Jooby, RpcServerType.Ktor -> {
                                val jarTaskExists = tasks.findByName("jar") != null
                                val customJarTaskName = if (jarTaskExists) "shadowJar" else "jar"
                                if (jsMainExists && wasmJsMainExists) {
                                    createCustomJarTask("jarWithJs", "js")
                                    createCustomJarTask("jarWithWasmJs", "wasmJs")
                                } else if (jsMainExists) {
                                    createCustomJarTask(customJarTaskName, "js")
                                } else {
                                    createCustomJarTask(customJarTaskName, "wasmJs")
                                }
                                if (jarTaskExists && (!jsMainExists || !wasmJsMainExists)) {
                                    tasks.getByName("jar", Jar::class).apply {
                                        enabled = false
                                        dependsOn("shadowJar")
                                    }
                                }
                            }

                            RpcServerType.SpringBoot -> {
                                val webArchive = if (jsMainExists) "jsArchive" else "wasmJsArchive"
                                val webClasses = if (jsMainExists) "jsMainClasses" else "wasmJsMainClasses"
                                tasks.getByName("bootJar", BootJar::class) {
                                    dependsOn(webArchive, webClasses)
                                    classpath = files(
                                        kotlinMppExtension.targets["jvm"].compilations["main"].output.allOutputs,
                                        project.configurations["jvmRuntimeClasspath"],
                                        (project.tasks[webArchive] as Jar).archiveFile
                                    )
                                }
                                if (jsMainExists && wasmJsMainExists) {
                                    tasks.create("jarWithJs") {
                                        group = KILUA_RPC_TASK_GROUP
                                        description = "Assembles a jar archive containing application with js frontend."
                                        dependsOn("bootJar")
                                    }
                                    tasks.create("jarWithWasmJs", BootJar::class) {
                                        group = KILUA_RPC_TASK_GROUP
                                        description =
                                            "Assembles a jar archive containing application with wasmJs frontend."
                                        dependsOn("wasmJsArchive", "wasmJsMainClasses")
                                        classpath = files(
                                            kotlinMppExtension.targets["jvm"].compilations["main"].output.allOutputs,
                                            project.configurations["jvmRuntimeClasspath"],
                                            (project.tasks["wasmJsArchive"] as Jar).archiveFile
                                        )
                                    }
                                } else {
                                    tasks.getByName("jar", Jar::class).apply {
                                        dependsOn("bootJar")
                                    }
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
                                val webArchive = if (jsMainExists) "jsArchive" else "wasmJsArchive"
                                tasks.getByName("shadowJar", ShadowJar::class) {
                                    dependsOn(webArchive)
                                    from(project.tasks[webArchive].outputs.files)
                                    mergeServiceFiles()
                                }
                                if (jsMainExists && wasmJsMainExists) {
                                    tasks.create("jarWithJs") {
                                        dependsOn("shadowJar")
                                        group = KILUA_RPC_TASK_GROUP
                                        description = "Assembles a jar archive containing application with js frontend."
                                    }
                                    tasks.create("jarWithWasmJs", ShadowJar::class) {
                                        dependsOn("wasmJsArchive")
                                        group = KILUA_RPC_TASK_GROUP
                                        description =
                                            "Assembles a jar archive containing application with wasmJs frontend."
                                        from(project.tasks["wasmJsArchive"].outputs.files)
                                        mergeServiceFiles()
                                    }
                                } else {
                                    tasks.getByName("jar", Jar::class).apply {
                                        enabled = false
                                        dependsOn("shadowJar")
                                    }
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
                                val webArchive = if (jsMainExists) "jsArchive" else "wasmJsArchive"
                                tasks.getByName("shadowJar", ShadowJar::class) {
                                    dependsOn(webArchive)
                                    from(project.tasks[webArchive].outputs.files)
                                }
                                if (jsMainExists && wasmJsMainExists) {
                                    tasks.create("jarWithJs") {
                                        dependsOn("shadowJar")
                                        group = KILUA_RPC_TASK_GROUP
                                        description = "Assembles a jar archive containing application with js frontend."
                                    }
                                    tasks.create("jarWithWasmJs", ShadowJar::class) {
                                        dependsOn("wasmJsArchive")
                                        group = KILUA_RPC_TASK_GROUP
                                        description =
                                            "Assembles a jar archive containing application with wasmJs frontend."
                                        from(project.tasks["wasmJsArchive"].outputs.files)
                                    }
                                } else {
                                    tasks.getByName("jar", Jar::class).apply {
                                        enabled = false
                                        dependsOn("shadowJar")
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

    private fun KiluaRpcPluginContext.createArchiveTask(prefix: String, appendix: String, assetsPath: String) {
        tasks.create("${prefix}Archive", Jar::class).apply {
            dependsOn("${prefix}BrowserDistribution")
            group = KILUA_RPC_TASK_GROUP
            description = "Assembles a jar archive containing $prefix web application."
            archiveAppendix.set(appendix)
            val distribution =
                project.tasks.getByName(
                    "${prefix}BrowserProductionWebpack",
                    KotlinWebpack::class
                ).outputDirectory
            from(distribution) {
                include("*.*")
            }
            val processedResources =
                project.tasks.getByName("${prefix}ProcessResources", Copy::class).destinationDir
            from(processedResources)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into(assetsPath)
            inputs.files(distribution, processedResources)
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

    private fun KiluaRpcPluginContext.createCustomJarTask(name: String, webPrefix: String) {
        tasks.create(name, Jar::class).apply {
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
                    )
                )
            }
            val dependencies = files(
                configurations.getByName("jvmRuntimeClasspath"),
                project.tasks["jvmJar"].outputs.files,
                project.tasks["${webPrefix}Archive"].outputs.files
            )
            from(dependencies.asSequence().map { if (it.isDirectory) it else zipTree(it) }
                .asIterable())
            exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
            inputs.files(dependencies)
            outputs.file(archiveFile)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

    private fun getServerType(project: Project): RpcServerType? {
        // To enable packaging tasks for Spring Boot MVC
        if (project.configurations["jvmMainImplementation"].dependencies.any { it.name == "spring-boot-starter-web" }) {
            return RpcServerType.SpringBoot
        }
        val rpcServerDependency = project.configurations["commonMainApi"].dependencies.map {
            it.name
        }.firstOrNull { it.startsWith("kilua-rpc-") }
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
    }

    public companion object {

        public const val KILUA_RPC_TASK_GROUP: String = "kilua-rpc"
        public const val PACKAGE_TASK_GROUP: String = "package"

    }

}
