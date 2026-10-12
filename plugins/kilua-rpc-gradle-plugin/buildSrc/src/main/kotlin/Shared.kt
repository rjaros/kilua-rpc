import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.net.URI

// A subset of the root buildSrc's Shared.kt, holding only what the Gradle plugin's own build needs.
// The plugin is an included build now, and an included build does not see the root buildSrc - nor
// does it inherit the root's group and version - so the few helpers it relies on are repeated here.
// Keep them in step with buildSrc/src/main/kotlin/Shared.kt.

fun KotlinJvmProjectExtension.kotlinJvmTargets(target: String = "25") {
    jvmToolchain {
        it.languageVersion.set(JavaLanguageVersion.of(target))
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

fun Project.setupDokka() {
    tasks.register("javadocJar", Jar::class.java) {
        // javadocJar is empty, but it is required for publishing to Maven Central. Dokka generates the actual documentation.
        it.archiveClassifier.set("javadoc")
    }

    extensions.getByType(DokkaExtension::class.java).run {
        dokkaSourceSets.configureEach {
            it.sourceLink {
                it.localDirectory.set(projectDir.resolve("src"))
                it.remoteUrl.set(URI("https://github.com/rjaros/kilua-rpc/tree/main/plugins/${project.name}/src"))
                it.remoteLineSuffix.set("#L")
            }
        }
    }
}
