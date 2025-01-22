import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.net.URI

fun KotlinMultiplatformExtension.compilerOptions() {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
                }
            }
        }
    }
}

fun KotlinMultiplatformExtension.kotlinJsTargets(withNode: Boolean = true) {
    js(IR) {
        useEsModules()
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        if (withNode) {
            nodejs {
                testTask {
                    useMocha()
                }
            }
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.kotlinWasmTargets(withNode: Boolean = true) {
    wasmJs {
        useEsModules()
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        if (withNode) {
            nodejs {
                testTask {
                    useMocha()
                }
            }
        }
    }
}

fun KotlinMultiplatformExtension.kotlinJvmTargets(target: String = "17") {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(target))
    }
    jvm {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xjsr305=strict")
                }
            }
        }
    }
}

fun KotlinJvmProjectExtension.kotlinJvmTargets(target: String = "17") {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(target))
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
        license {
            name.set("MIT")
            url.set("https://opensource.org/licenses/MIT")
            distribution.set("https://opensource.org/licenses/MIT")
        }
    }
    developers {
        developer {
            id.set("rjaros")
            name.set("Robert Jaros")
            url.set("https://github.com/rjaros/")
        }
    }
    scm {
        url.set(kiluaRpcUrl)
        connection.set(kiluaRpcVcsUrl)
        developerConnection.set(kiluaRpcVcsUrl)
    }
}

fun Project.setupPublishing() {
    val isSnapshot = hasProperty("SNAPSHOT")
    extensions.getByType<PublishingExtension>().run {
        publications.withType<MavenPublication>().all {
            if (!isSnapshot) artifact(tasks["javadocJar"])
            pom {
                defaultPom()
            }
        }
    }
    extensions.getByType<SigningExtension>().run {
        if (!isSnapshot) {
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
    // Workaround https://github.com/gradle/gradle/issues/26091
    tasks.withType<AbstractPublishToMaven>().configureEach {
        val signingTasks = tasks.withType<Sign>()
        mustRunAfter(signingTasks)
    }
}

fun Project.setupDokka(provider: TaskProvider<DokkaBaseTask>) {
    tasks.register<Jar>("javadocJar") {
        dependsOn(provider)
        from(provider.map { it.outputs })
        archiveClassifier.set("javadoc")
    }

    extensions.getByType<DokkaExtension>().run {
        dokkaSourceSets.invoke {
            configureEach {
                sourceLink {
                    localDirectory.set(projectDir.resolve("src"))
                    remoteUrl.set(URI("https://github.com/rjaros/kilua-rpc/tree/master/modules/${project.name}/src"))
                    remoteLineSuffix.set("#L")
                }
            }
        }
    }
}
