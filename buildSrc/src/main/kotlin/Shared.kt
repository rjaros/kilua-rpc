import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import com.android.build.api.withAndroid
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.net.URI

fun KotlinMultiplatformExtension.compilerOptions(withWasmMetadata: Boolean = false) {
    targets.configureEach { target ->
        val targetName = target.name
        target.compilations.configureEach { compilation ->
            compilation.compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
                    if (targetName == "wasmJs" || targetName == "js" || (withWasmMetadata && targetName == "metadata")) {
                        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
                    }
                }
            }
        }
    }
}

fun KotlinMultiplatformExtension.kotlinJsTargets(withNode: Boolean = true) {
    js {
        useEsModules()
        browser {
            testTask {
                it.useKarma {
                    useChromeHeadless()
                }
            }
        }
        if (withNode) {
            nodejs {
            }
        }
        compilerOptions {
            target.set("es2015")
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.kotlinWasmTargets(withNode: Boolean = true) {
    wasmJs {
        useEsModules()
        browser {
            testTask {
                it.useKarma {
                    useChromeHeadless()
                }
            }
        }
        if (withNode) {
            nodejs {
            }
        }
        compilerOptions {
            target.set("es2015")
        }
    }
}

fun KotlinMultiplatformExtension.kotlinJvmTargets(target: String = "25") {
    jvmToolchain {
        it.languageVersion.set(JavaLanguageVersion.of(target))
    }
    jvm {
        compilations.configureEach {
            it.compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xjsr305=strict")
                }
            }
        }
    }
}

fun KotlinJvmProjectExtension.kotlinJvmTargets(target: String = "25") {
    jvmToolchain {
        it.languageVersion.set(JavaLanguageVersion.of(target))
    }
}

/**
 * API level the android artefacts are compiled against.
 *
 * AGP 9 refuses to combine `com.android.library` with `org.jetbrains.kotlin.multiplatform`, so the
 * android target of a multiplatform module comes from `com.android.kotlin.multiplatform.library`
 * and is configured through an `android {}` block of the kotlin extension rather than a top level
 * `android` block. That block is contributed by the Android Gradle plugin as a target preset, so
 * it is only visible in the build script of a module that applies the plugin - which is why the
 * android target is configured there and not from this file.
 */
const val androidCompileSdk = 37

/**
 * Lowest android api level the client supports.
 */
const val androidMinSdk = 21

/**
 * Bytecode level of the android compilations. Deliberately lower than the JVM toolchain, so that
 * the tooling the plugin drives stays able to read the output.
 */
val androidJvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11

/**
 * The default hierarchy template extended with a `jvmAndAndroid` group.
 *
 * The default template keeps the Android target out of the `jvm` group, so without this the
 * android target would not see a single line of the JVM client implementation. Sharing the
 * client half is what keeps the android flavour from becoming a third copy of it - the server
 * half stays in `jvmMain` and is therefore not published for android.
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun KotlinMultiplatformExtension.kiluaHierarchyTemplate() {
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                // Not the built-in withAndroidTarget(): the android target of a multiplatform
                // module is created by AGP and is not a KGP KotlinAndroidTarget.
                @Suppress("UnstableApiUsage")
                withAndroid()
            }
        }
    }
}

const val kiluaRpcProjectName = "Kilua RPC"
const val kiluaRpcProjectDescription = "Fullstack RPC library for Kotlin/Wasm and Kotlin/JS"
const val kiluaRpcUrl = "https://github.com/rjaros/kilua-rpc"
const val kiluaRpcVcsUrl = "scm:git:git://github.com/rjaros/kilua-rpc.git"

fun MavenPom.defaultPom() {
    name.set(kiluaRpcProjectName)
    description.set(kiluaRpcProjectDescription)
    inceptionYear.set("2024")
    url.set(kiluaRpcUrl)
    licenses {
        it.license {
            it.name.set("MIT")
            it.url.set("https://opensource.org/licenses/MIT")
            it.distribution.set("https://opensource.org/licenses/MIT")
        }
    }
    developers {
        it.developer {
            it.id.set("rjaros")
            it.name.set("Robert Jaros")
            it.url.set("https://github.com/rjaros/")
        }
    }
    scm {
        it.url.set(kiluaRpcUrl)
        it.connection.set(kiluaRpcVcsUrl)
        it.developerConnection.set(kiluaRpcVcsUrl)
    }
}

fun Project.setupPublishing() {
    val isSnapshot = hasProperty("SNAPSHOT")
    extensions.getByType(PublishingExtension::class.java).run {
        publications.withType(MavenPublication::class.java).all {
            if (!isSnapshot) it.artifact(this@setupPublishing.tasks.getByName("javadocJar"))
            it.pom {
                it.defaultPom()
            }
        }
    }
    extensions.getByType(SigningExtension::class.java).run {
        if (!isSnapshot) {
            sign(extensions.getByType(PublishingExtension::class.java).publications)
        }
    }
    // Workaround https://github.com/gradle/gradle/issues/26091
    tasks.withType(AbstractPublishToMaven::class.java).configureEach {
        val signingTasks = tasks.withType(Sign::class.java)
        it.mustRunAfter(signingTasks)
    }
}

fun Project.setupDokka(provider: TaskProvider<DokkaGeneratePublicationTask>) {
    tasks.register("javadocJar", Jar::class.java) {
        it.from(provider.flatMap { it.outputDirectory })
        it.archiveClassifier.set("javadoc")
    }

    extensions.getByType(DokkaExtension::class.java).run {
        dokkaSourceSets.configureEach {
            it.sourceLink {
                it.localDirectory.set(projectDir.resolve("src"))
                it.remoteUrl.set(URI("https://github.com/rjaros/kilua-rpc/tree/main/modules/${project.name}/src"))
                it.remoteLineSuffix.set("#L")
            }
        }
    }
}
