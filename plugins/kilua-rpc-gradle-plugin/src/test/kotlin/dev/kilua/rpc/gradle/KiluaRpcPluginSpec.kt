/*
 * Copyright (c) 2026 Robert Jaros
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

import de.infix.testBalloon.framework.core.testSuite
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The path relative to a project's build directory that the plugin points `commonMain` at.
 *
 * Spelled out rather than imported from the processor: the processor is a separate published
 * artifact and the plugin cannot depend on it.
 */
private const val COMMON_MAIN_KSP_DIR = "build/generated/ksp/metadata/commonMain/kotlin"

private fun kotlin(project: Project): KotlinMultiplatformExtension =
    project.extensions.getByType(KotlinMultiplatformExtension::class.java)

/**
 * A project with the Kotlin multiplatform, KSP and Kilua plugins applied, already evaluated.
 *
 * `Project.evaluate()` is not public API in Gradle 9 and `ProjectBuilder` never evaluates on its own,
 * so the project is built under a bare parent and that parent is asked to evaluate it. Without that
 * last step every `afterEvaluate` hook the plugin registers silently never runs, which is the
 * difference between a test that proves something and one that only looks like it does.
 */
private fun kiluaProject(
    withJvm: Boolean = true,
    withJs: Boolean = true,
    withWasmJs: Boolean = false,
    withLinux: Boolean = false,
    withMainClass: Boolean = true,
    mainClassName: String? = null,
    configure: Project.() -> Unit = {}
): Project {
    val root = ProjectBuilder.builder().withName("root").build()
    val project = ProjectBuilder.builder().withParent(root).withName("consumer").build()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    project.pluginManager.apply("com.google.devtools.ksp")
    project.pluginManager.apply("dev.kilua.rpc")
    val kotlin = kotlin(project)
    if (withJvm) {
        kotlin.jvm {
            if (withMainClass) mainRun { mainClass.set("dev.kilua.rpc.gradle.Noop") }
        }
    }
    if (withJs) kotlin.js { nodejs() }
    if (withWasmJs) kotlin.wasmJs { nodejs() }
    // A library target, so no binaries: declaring the target is all the plugin has to react to.
    if (withLinux) kotlin.linuxX64()
    // Spring Boot and Vert.x read the entry point from an extra property rather than from the jvm
    // run task, which is what makes it a property of the consumer's build script.
    if (mainClassName != null) project.extensions.extraProperties.set("mainClassName", mainClassName)
    project.configure()
    root.evaluationDependsOn(":consumer")
    return project
}

/**
 * Declares a dependency by coordinates, as a consumer's build script would.
 *
 * The version is irrelevant: the plugin only ever inspects `group:name` to tell a server backbone
 * from an imposter, and nothing is resolved. Callers pass an obviously fake one on purpose.
 */
private fun Project.declare(coordinates: String) {
    configurations.findByName("commonMainImplementation")?.dependencies?.add(
        dependencies.create(coordinates)
    )
}

/**
 * The innermost cause of a configuration failure.
 *
 * `evaluationDependsOn` reports a failure as `ProjectConfigurationException: A problem occurred
 * configuring project ':consumer'`, so the message the plugin actually produced is always further
 * down the chain.
 */
private fun rootCause(failure: Throwable): Throwable =
    failure.cause?.let { if (it === failure) it else rootCause(it) } ?: failure

val KiluaRpcPluginSpec by testSuite {

    test("a project without the Kotlin multiplatform plugin gets the extension and nothing else") {
        val project = ProjectBuilder.builder().withName("plain").build()
        project.pluginManager.apply("dev.kilua.rpc")
        // The extension is created before the withId check, so it is always there.
        assertNotNull(project.extensions.findByType(KiluaRpcExtension::class.java))
        // And none of the multiplatform wiring exists on this project.
        assertNull(project.tasks.findByName("generateKiluaRpcSources"))
    }

    test("a project with fewer than two platform targets fails naming how many it found") {
        val failure = assertFailsWith<Exception> { kiluaProject(withJvm = true, withJs = false) }
        val message = rootCause(failure).message.orEmpty()
        assertTrue(
            message.contains("at least two Kotlin multiplatform targets"),
            "expected the guard's message, was: $message"
        )
        assertTrue(message.contains("1"), "expected the target count to be reported, was: $message")
    }

    test("a project with two platform targets configures without complaint") {
        val project = kiluaProject()
        assertNotNull(project.tasks.findByName("kspCommonMainKotlinMetadata"))
    }

    test("commonMain is pointed at the metadata output directory") {
        val project = kiluaProject()
        val commonMain = kotlin(project).sourceSets.getByName("commonMain")
        assertTrue(
            commonMain.kotlin.srcDirs.any { it.path.endsWith(COMMON_MAIN_KSP_DIR) },
            "expected the plugin to register $COMMON_MAIN_KSP_DIR, got ${commonMain.kotlin.srcDirs}"
        )
        // That the plugin registers nothing for the platform source sets is deliberately not
        // asserted here: KSP registers those directories itself, with exactly the path the plugin
        // would use, so a duplicate registration would collapse into the same set and be
        // indistinguishable. It is covered by deleting the registrations and recompiling, as was done
        // when the four were reduced to this one.
    }

    test("enableGradleTasks set through the DSL actually suppresses the Gradle tasks") {
        // Read at configuration time, and it was read before the DSL block ran - so this is the
        // regression test for the plugin having ignored what the build script said.
        val suppressed = kiluaProject {
            extensions.getByType(KiluaRpcExtension::class.java).enableGradleTasks.set(false)
        }
        assertNull(suppressed.tasks.findByName("generateKiluaRpcSources"))
        assertNull(suppressed.tasks.findByName("jsArchive"))
        assertNull(suppressed.tasks.findByName("jarWithJs"))

        val kept = kiluaProject()
        assertNotNull(kept.tasks.findByName("generateKiluaRpcSources"))
    }

    test("the server backbone is recognized by group as well as by artifact name") {
        val ktor = kiluaProject { declare("dev.kilua:kilua-rpc-ktor:1.0") }
        assertNotNull(ktor.tasks.findByName("jarWithJs"), "a dev.kilua ktor backbone should be packaged")

        val imposter = kiluaProject { declare("com.example:kilua-rpc-ktor:1.0") }
        assertNull(
            imposter.tasks.findByName("jarWithJs"),
            "a same-named artifact from another group must not be taken for a server backbone"
        )
    }

    test("a Spring Boot consumer with no main class name fails naming the problem") {
        val failure = assertFailsWith<Exception> {
            kiluaProject {
                declare("dev.kilua:kilua-rpc-spring-boot:1.0")
            }
        }
        val message = rootCause(failure).message.orEmpty()
        assertTrue(
            message.contains("mainClassName"),
            "expected a message naming mainClassName, was: $message"
        )
    }

    test("a Spring Boot consumer with no application subproject still configures") {
        // The plugin warns rather than silently doing nothing when it cannot delegate jvm run. The
        // warning itself is not asserted: ProjectBuilder exposes no handle on the build output, and
        // the observable half is that configuration succeeds instead of failing.
        val project = kiluaProject(mainClassName = "example.MainKt") {
            declare("dev.kilua:kilua-rpc-spring-boot:1.0")
        }
        assertNotNull(project.tasks.findByName("jarWithJs"))
    }

    test("a project with no JVM target skips the whole server distribution block") {
        val project = kiluaProject(withJvm = false, withJs = true, withWasmJs = true) {
            declare("dev.kilua:kilua-rpc-ktor:1.0")
        }
        // Each of these would throw during configuration without the guard that skips the block.
        assertNull(project.tasks.findByName("jvmProcessResources"))
        assertNull(project.tasks.findByName("jsArchive"))
        assertNull(project.tasks.findByName("jvmRun"))
    }

    test("every compilation depends on the task that writes the common half") {
        val project = kiluaProject()
        for (name in listOf("compileKotlinJvm", "compileKotlinJs", "kspKotlinJvm", "kspKotlinJs")) {
            val task = project.tasks.findByName(name)
            assertNotNull(task, "$name should exist")
            val dependencies = task.taskDependencies.getDependencies(task).map { it.name }
            assertContains(
                dependencies,
                "kspCommonMainKotlinMetadata",
                "$name should depend on the common half task"
            )
        }
        assertEquals("Kilua RPC", project.tasks.findByName("generateKiluaRpcSources")?.group)
    }

    test("a native target counts as a platform and is wired like any other") {
        // The wiring is driven off the targets rather than a list of task names, so a target added
        // after this was written has to arrive already working. Both halves are checked: the ksp
        // configuration the processor is added to, and the dependency on the common half.
        val project = kiluaProject(withLinux = true)
        assertNotNull(
            project.configurations.findByName("kspLinuxX64"),
            "the processor should be added to a kspLinuxX64 configuration"
        )
        for (name in listOf("kspKotlinLinuxX64", "compileKotlinLinuxX64")) {
            val task = project.tasks.findByName(name)
            assertNotNull(task, "$name should exist")
            val dependencies = task.taskDependencies.getDependencies(task).map { it.name }
            assertContains(
                dependencies,
                "kspCommonMainKotlinMetadata",
                "$name should depend on the common half task"
            )
        }
    }

    test("a native-only client passes the two target guard") {
        // jvm + linuxX64 is two targets, which is the minimum the generated code splits against.
        val project = kiluaProject(withJs = false, withLinux = true)
        assertNotNull(project.tasks.findByName("kspCommonMainKotlinMetadata"))
    }

    test("a single native target alone fails naming how many it found") {
        val failure = assertFailsWith<Exception> {
            kiluaProject(withJvm = false, withJs = false, withLinux = true)
        }
        val message = rootCause(failure).message.orEmpty()
        assertTrue(
            message.contains("at least two Kotlin multiplatform targets"),
            "expected the guard's message, was: $message"
        )
        assertTrue(message.contains("linuxX64"), "expected the target name to be listed, was: $message")
    }
}
