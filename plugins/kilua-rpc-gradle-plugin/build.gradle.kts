plugins {
    `kotlin-dsl`
    kotlin("jvm")
    id("java-gradle-plugin")
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    id("maven-publish")
    id("signing")
    alias(libs.plugins.gradle.plugin.publish)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom("../../detekt-config.yml")
    buildUponDefaultConfig = true
}

@Suppress("UnstableApiUsage")
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
    kotlinJvmTargets()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.tomlj)
}

tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

tasks.getByName("dokkaHtml").apply {
    enabled = !project.hasProperty("SNAPSHOT")
}

tasks.getByName("jar", Jar::class) {
    from(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")) {
        rename { "dev.kilua.rpc.versions.toml" }
        filter { line -> line.replaceAfter("kilua-rpc = ", "\"${version}\"") }
    }
}

publishing {
    publications {
        withType<MavenPublication>() {
            pom {
                defaultPom()
            }
        }
    }
}

extensions.getByType<SigningExtension>().run {
    isRequired = !project.hasProperty("SNAPSHOT")
    sign(extensions.getByType<PublishingExtension>().publications)
}

nmcp {
    publishAllPublications {}
}
