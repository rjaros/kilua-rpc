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

tasks {
    val versionsToml = rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")
    val version = project.version.toString()

    getByName("jar", Jar::class) {
        from(versionsToml) {
            rename { "dev.kilua.rpc.versions.toml" }
            filter { line -> line.replaceAfter("kilua-rpc = ", "\"${version}\"") }
        }
    }
}

// The plugin reads its version out of this resource, which the jar task above injects into the jar
// alone. Tests run against the classes directory rather than the jar, so without this the read
// returns null and applying the plugin fails outright.
//
// The copy is verbatim rather than version-substituted: that substitution exists so a published
// plugin pins the processor coordinate to its own version, and tests never resolve that coordinate,
// so rewriting the file here would only add a configuration-cache-hostile filter for no gain.
//
// A Copy rather than a Sync, deliberately: it writes into the directory processTestResources owns,
// and a Sync would strip anything else already written there, so adding test resources later would
// silently delete them depending on task ordering.
val versionsResourceForTests = tasks.register<Copy>("versionsResourceForTests") {
    from(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
    rename { "dev.kilua.rpc.versions.toml" }
    into(layout.buildDirectory.dir("resources/test"))
}

tasks.withType<Test>().configureEach {
    dependsOn(versionsResourceForTests)
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

setupDokka(tasks.dokkaGeneratePublicationHtml)
