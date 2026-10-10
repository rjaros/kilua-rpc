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
import io.quarkus.gradle.extension.QuarkusPluginExtension
import io.quarkus.gradle.tasks.QuarkusBuild
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
    Javalin, Jooby, Ktor, Micronaut, Quarkus, SpringBoot, VertX
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

        // Kilua RPC's generated code always comes in two halves: a common one holding the shared
        // declarations, and one per platform target. The common half can only be produced by the KSP
        // task that runs the metadata compilation, and KSP registers that task only for a project
        // with at least two real platform targets. A project with a single platform target therefore
        // cannot build no matter how the dependsOn edges are wired - so fail here with something
        // actionable instead of letting the compiler report a bare ACTUAL_WITHOUT_EXPECT later.
        val platformTypes = setOf(
            KotlinPlatformType.jvm,
            KotlinPlatformType.js,
            KotlinPlatformType.wasm,
            // The android target of a multiplatform module is contributed by AGP rather than by a
            // KotlinGradlePlugin target, but it still reports a platform type.
            KotlinPlatformType.androidJvm
        )
        if (enableKsp) {
            project.afterEvaluate {
                val platformTargets = kotlinMppExtension.targets.filter { it.platformType in platformTypes }
                if (platformTargets.size < 2) {
                    error(
                        "The dev.kilua.rpc plugin needs at least two Kotlin multiplatform targets, but " +
                            "project '${project.name}' declares ${platformTargets.size}: " +
                            "${platformTargets.map { it.name }}. The generated service code is split " +
                            "into a common half and one half per platform, and the common half is " +
                            "produced by a KSP task that only exists once a project has two or more " +
                            "targets. Add a second target (for example js() or wasmJs()) instead of " +
                            "leaving the project with a single platform."
                    )
                }
            }
        }

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
            // Matched by name and used as a collection, never as a name string: KSP only creates
            // kspCommonMainKotlinMetadata once a project has at least two KMP targets, so a single
            // target project leaves this empty and contributes no dependency, whereas depending on
            // the task name itself would fail the build when the compile task finally runs.
            val commonMetadataKspTask = project.tasks.matching { it.name == "kspCommonMainKotlinMetadata" }
            project.tasks.all.compileKotlinJs.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }
            project.tasks.all.compileKotlinWasmJs.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }
            project.tasks.all.compileKotlinJvm.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }
            project.dependencies.add("kspCommonMainMetadata", "dev.kilua:kilua-rpc-ksp-processor:${kiluaRpcVersion}")
            kotlinMppExtension.targets
                .matching { it.platformType in platformTypes }.configureEach {
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
            // Only the commonMain half has to be pointed at a directory by hand. KSP registers its
            // own output for the js, wasmJs, jvm and android compilations, so adding those source
            // directories here would be redundant; adding them anyway is what made it easy to
            // assume otherwise when the output layout changed.
            //
            // The common half is written by a separate task, the metadata one, and nothing wires
            // that output into the commonMain source set automatically - which is why a consumer
            // that forgot this sees "has no corresponding 'expect' declaration" from every platform.
            kotlinMppExtension.sourceSets.configureEach {
                if (it.name == "commonMain") it.kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            }
            project.tasks.all.kspKotlinJs.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }
            project.tasks.all.kspKotlinWasmJs.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }
            project.tasks.all.kspKotlinJvm.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }
            // The android compilation reaches the commonMain ksp output through kspAndroidMain, and
            // unlike the jvm/js/wasm compilations that edge is not wired anywhere else. The task is
            // matched by name because AGP is not on this plugin's classpath, so there is no typed
            // accessor to attach it to. See the comment on commonMetadataKspTask above: relying on the
            // empty collection rather than on the task name is what keeps a single target project,
            // where KSP creates no metadata task at all, from failing the build.
            project.tasks.matching { it.name == "kspAndroidMain" }.configureEach {
                it.dependsOn(commonMetadataKspTask)
            }

            // Registered afterEvaluate for the same reason the packaging block below is: reading the
            // extension any earlier happens while the kotlin("multiplatform") plugin is still being
            // applied, which is before the build script body - and therefore before any `kilua-rpc { }`
            // DSL block - has run, so a value configured there would be silently ignored.
            project.afterEvaluate {
                if (kiluaRpcExtension.enableGradleTasks.get()) {
                    project.tasks.register("generateKiluaRpcSources") {
                        it.group = KILUA_RPC_TASK_GROUP
                        it.description = "Generating Kilua RPC sources"
                        it.dependsOn(commonMetadataKspTask)
                    }
                }
            }
        }

        project.afterEvaluate outer@{
            if (!kiluaRpcExtension.enableGradleTasks.get()) return@outer
            // Everything below assembles a server distribution: boot jars, shadow jars, web
            // archives and the resource handling that goes with them. A consumer that has no JVM
            // target - an android only client - has no jvmProcessResources, no jvmRun and no jar
            // to assemble, so every one of those lookups would fail the build for no reason.
            if (kotlinMppExtension.targets.none { it.platformType == KotlinPlatformType.jvm }) {
                return@outer
            }
            it.afterEvaluate {
                val serverType = getServerType(project)
                val assetsPath = when (serverType) {
                    RpcServerType.Micronaut, RpcServerType.SpringBoot -> "/public"
                    RpcServerType.Quarkus -> "/META-INF/resources"
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
                        val mainClassName = it.requireExtraMainClassName()
                        if (isJsTarget)
                            createBootJarTask("jarWithJs", "js", mainClassName, kotlinMppExtension)
                        if (isWasmJsTarget)
                            createBootJarTask("jarWithWasmJs", "wasmJs", mainClassName, kotlinMppExtension)
                        it.tasks.findByName("jar")?.enabled = false
                        it.runApplicationSubproject("bootRun")
                    }

                    RpcServerType.Micronaut -> {
                        if (isJsTarget) createShadowJarTask("jarWithJs", "js")
                        if (isWasmJsTarget) createShadowJarTask("jarWithWasmJs", "wasmJs")
                        it.tasks.findByName("jar")?.enabled = false
                        it.runApplicationSubproject("run")
                    }

                    RpcServerType.Quarkus -> {
                        if (isJsTarget) createQuarkusJarTask("jarWithJs", "js")
                        if (isWasmJsTarget) createQuarkusJarTask("jarWithWasmJs", "wasmJs")
                        it.runApplicationSubproject("quarkusDev")
                    }

                    RpcServerType.VertX -> {
                        val mainClassName = it.requireExtraMainClassName()
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

    /**
     * Read the `mainClassName` extra property Spring Boot and Vert.x consumers set, and fail with a
     * message naming the project rather than letting a bare MissingPropertyException surface.
     */
    private fun Project.requireExtraMainClassName(): String =
        if (extra.has("mainClassName")) {
            extra.get("mainClassName")?.toString()
                ?: error("The \"mainClassName\" extra property of project $name is set to null.")
        } else {
            error(
                "The dev.kilua.rpc plugin packages a server distribution for project $name but no " +
                    "\"mainClassName\" extra property is set. Add `extra[\"mainClassName\"] = \"<fully " +
                    "qualified class>\"` to this build script."
            )
        }

    /**
     * Point `jvmRun` at the dedicated application subproject some frameworks need to actually start
     * the server, warning instead of silently doing nothing when that subproject is absent.
     */
    private fun Project.runApplicationSubproject(taskName: String) {
        val applications = subprojects.filter { it.name == "application" }
        if (applications.isEmpty()) {
            logger.warn(
                "The dev.kilua.rpc plugin expected an \"application\" subproject to delegate `jvmRun` " +
                    "to `$taskName`, but found none, so `jvmRun` will not start the server."
            )
            return
        }
        applications.forEach { tasks.getByName("jvmRun").dependsOn("${it.path}:$taskName") }
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
                        "Implementation-Version" to project.rootProject.version
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
            jar.exclude(*SIGNATURE_FILE_EXCLUSIONS)
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

    private fun KiluaRpcPluginContext.createQuarkusJarTask(name: String, webPrefix: String) {
        val appProject = project.subprojects.firstOrNull { it.name == "application" }
        if (appProject == null) {
            project.logger.warn("Kilua RPC: project 'application' not found to assemble Quarkus application '$name'.")
            return
        }
        val archiveJarProvider = project.tasks.named("${webPrefix}Archive", Jar::class.java).flatMap { it.archiveFile }
        appProject.configurations.configureEach { configuration ->
            if (configuration.name == "runtimeOnly") {
                appProject.dependencies.add(
                    "runtimeOnly",
                    appProject.files(
                        appProject.provider {
                            if (project.gradle.taskGraph.hasTask(project.tasks.named(name).get())) {
                                appProject.files(archiveJarProvider)
                            } else {
                                appProject.files()
                            }
                        }
                    )
                )
            }
        }
        appProject.afterEvaluate {
            appProject.tasks.matching { it.name.startsWith("quarkus") }.configureEach { task ->
                task.dependsOn(project.tasks.named("${webPrefix}Archive"))
                task.outputs.cacheIf { false }
            }
            appProject.tasks.withType(QuarkusBuild::class.java).configureEach {
                appProject.extensions.getByType(QuarkusPluginExtension::class.java)
                    .quarkusBuildProperties.put("quarkus.package.type", "uber-jar")
            }
        }
        val runnerJarProvider = project.provider {
            val finalName =
                appProject.extensions.findByType(QuarkusPluginExtension::class.java)?.finalName?.orNull
                    ?: appProject.name
            appProject.layout.buildDirectory.file("$finalName-runner.jar").get().asFile
        }
        project.tasks.register(name, Jar::class.java) { jar ->
            jar.group = KILUA_RPC_TASK_GROUP
            jar.description = "Assembles a fat jar archive containing application with $webPrefix frontend."
            kiluaRpcExtension.jarArchiveFileName.orNull?.let {
                jar.archiveFileName.set(kiluaRpcExtension.jarArchiveFileName)
            }
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.dependsOn("${webPrefix}Archive", appProject.tasks.named("quarkusBuild"))
            jar.from(
                project.zipTree(
                    project.tasks.named("${webPrefix}Archive", Jar::class.java).flatMap { it.archiveFile }
                )
            )
            jar.from(project.provider { project.zipTree(runnerJarProvider.get()) })
            jar.doFirst {
                java.util.jar.JarFile(runnerJarProvider.get()).use { jarFile ->
                    val mainAttributes = jarFile.manifest?.mainAttributes ?: return@use
                    jar.manifest.attributes(
                        mainAttributes
                            .mapKeys { [key, _] -> key.toString() }
                            .filterValues { it != null }
                    )
                }
            }
        }
    }

    private val TaskContainer.all: TaskCollections get() = TaskCollections(this)

    /** Lazy task collections */
    private class TaskCollections(private val tasks: TaskContainer) {

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
        // A consumer does not have to be a fullstack project: a pure android client has no
        // jvmMain source set at all, so neither configuration can be assumed to exist.
        //
        // The kilua backbone artifacts are identified by group and name rather than by name alone,
        // so that an unrelated module merely called kilua-rpc-* in some other group is not mistaken
        // for one.
        val commonMainModules = project.dependencyModules("commonMainImplementation")
        when (commonMainModules.firstOrNull { it.isKiluaRpcServer() }?.suffixAfter("dev.kilua:kilua-rpc-")) {
            "javalin", "javalin-koin", "javalin-metro" -> return RpcServerType.Javalin
            "jooby", "jooby-koin", "jooby-metro" -> return RpcServerType.Jooby
            "ktor", "ktor-koin", "ktor-metro" -> return RpcServerType.Ktor
            "micronaut" -> return RpcServerType.Micronaut
            "quarkus" -> return RpcServerType.Quarkus
            "spring-boot" -> return RpcServerType.SpringBoot
            "vertx", "vertx-koin", "vertx-metro" -> return RpcServerType.VertX
        }
        val jvmMainModules = project.dependencyModules("jvmMainImplementation")
        when (jvmMainModules.firstOrNull { it.isKiluaSsrServer() }?.suffixAfter("dev.kilua:kilua-ssr-server-")) {
            "javalin" -> return RpcServerType.Javalin
            "jooby" -> return RpcServerType.Jooby
            "ktor" -> return RpcServerType.Ktor
            "micronaut" -> return RpcServerType.Micronaut
            "quarkus" -> return RpcServerType.Quarkus
            "spring-boot" -> return RpcServerType.SpringBoot
            "vertx" -> return RpcServerType.VertX
        }
        // Fallbacks on framework coordinates, matched by artifact name only. These are third party
        // coordinates no other plugin produces, so the name is enough to be unambiguous.
        val jvmMainNames = project.dependencyNames("jvmMainImplementation")
        when {
            "javalin" in jvmMainNames -> return RpcServerType.Javalin
            "jooby-kotlin" in jvmMainNames -> return RpcServerType.Jooby
            "ktor-server-core" in jvmMainNames || "ktor-server-core-jvm" in jvmMainNames ->
                return RpcServerType.Ktor
            "micronaut-runtime" in jvmMainNames -> return RpcServerType.Micronaut
            "quarkus-rest" in jvmMainNames || "quarkus-rest-jvm" in jvmMainNames ->
                return RpcServerType.Quarkus
            "spring-boot-starter-web" in jvmMainNames || "spring-boot-starter-webflux" in jvmMainNames ->
                return RpcServerType.SpringBoot
            "vertx-web" in jvmMainNames -> return RpcServerType.VertX
        }
        return null
    }

    private fun Project.dependencyModules(configurationName: String): List<String> =
        configurations.findByName(configurationName)?.dependencies?.map { "${it.group ?: ""}:${it.name}" }.orEmpty()

    private fun Project.dependencyNames(configurationName: String): List<String> =
        configurations.findByName(configurationName)?.dependencies?.map { it.name }.orEmpty()

    /** True for `dev.kilua:kilua-rpc-<suffix>` server backbone artifacts. */
    private fun String.isKiluaRpcServer(): Boolean = startsWith("dev.kilua:kilua-rpc-")

    /** True for `dev.kilua:kilua-ssr-server-<suffix>` server artifacts. */
    private fun String.isKiluaSsrServer(): Boolean = startsWith("dev.kilua:kilua-ssr-server-")

    /** The part of `"<group>:<name>"` after the given prefix, or null when the prefix does not match. */
    private fun String.suffixAfter(prefix: String): String? = takeIf { it.startsWith(prefix) }?.removePrefix(prefix)

    public companion object {

        public const val KILUA_RPC_TASK_GROUP: String = "Kilua RPC"
        public const val PACKAGE_TASK_GROUP: String = "package"

        private val SIGNATURE_FILE_EXCLUSIONS = arrayOf(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA"
        )
    }
}
