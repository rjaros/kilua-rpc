plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.test.balloon)
}

// This is an included build, so the root's `allprojects { group/version }` does not reach it and
// both have to be set here. The version must match the root's so that the processor coordinate the
// plugin injects resolves to the same version - it is redirected to the local project anyway, but
// keeping them equal keeps a published plugin and a published library in step.
group = "dev.kilua"
version = libs.versions.kilua.rpc.get() + if (hasProperty("SNAPSHOT")) "-SNAPSHOT" else ""

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    website.set(kiluaRpcUrl)
    vcsUrl.set(kiluaRpcVcsUrl)
    plugins {
        create("kiluaRpcGradlePlugin") {
            displayName = kiluaRpcProjectName
            description = kiluaRpcProjectDescription
            id = "dev.kilua.rpc"
            implementationClass = "dev.kilua.rpc.gradle.KiluaRpcPlugin"
            tags.set(
                listOf(
                    "kilua",
                    "rpc",
                    "kotlin",
                    "kotlin-js",
                    "kotlin-wasm",
                    "webassembly",
                    "kotlin-multiplatform"
                )
            )
        }
    }
}

kotlin {
    explicitApi()
    kotlinJvmTargets("21")
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.tomlj)
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.quarkus.gradle.plugin)
    testImplementation(kotlin("test"))
    testImplementation(libs.test.balloon)
}

tasks.processResources {
    // The plugin reads its own version out of a bundled copy of the catalog (see KiluaRpcPlugin.apply).
    // It has to sit on the main runtime classpath, not only in the jar: when the plugin is applied from
    // this included build Gradle may put the project's classes and resources on the consumer's classpath
    // directly, and a version written into the jar alone would then be missing and fail the apply. A
    // published plugin packages processResources output into the jar anyway, so one location covers both.
    val versionsToml = rootProject.layout.projectDirectory.file("../../gradle/libs.versions.toml")
    val pluginVersion = project.version.toString()

    // The version is threaded through a filter, which Gradle cannot see as an input on its own, so
    // without this a change of version (e.g. adding -PSNAPSHOT) would leave the resource stale.
    inputs.property("kiluaRpcPluginVersion", pluginVersion)
    from(versionsToml) {
        rename { "dev.kilua.rpc.versions.toml" }
        // Only the `[versions]` entry `kilua-rpc = "..."` is rewritten; the `[plugins]` alias
        // `kilua-rpc = { id = "dev.kilua.rpc", ... }` declares the same key and must be left alone.
        filter { line ->
            if (line.startsWith("kilua-rpc = \"")) {
                line.replaceAfter("kilua-rpc = ", "\"${pluginVersion}\"")
            } else {
                line
            }
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                defaultPom()
            }
        }
    }
}

tasks.getByName("dokkaGeneratePublicationHtml").run {
    enabled = !project.hasProperty("SNAPSHOT")
}

extensions.getByType<SigningExtension>().run {
    isRequired = !project.hasProperty("SNAPSHOT")
    sign(extensions.getByType<PublishingExtension>().publications)
}

setupDokka()
