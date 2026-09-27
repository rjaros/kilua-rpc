plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.gradle.plugin.publish)
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
