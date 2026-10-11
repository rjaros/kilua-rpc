plugins {
    kotlin("multiplatform") apply false
    `kotlin-dsl` apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.nmcp) apply false
    alias(libs.plugins.nmcp.aggregation)
    id("org.jetbrains.dokka")
    id("maven-publish")
}

val versionVal = libs.versions.kilua.rpc.asProvider().get()

allprojects {
    group = "dev.kilua"
    version = if (hasProperty("SNAPSHOT")) {
        "$versionVal-SNAPSHOT"
    } else {
        versionVal
    }
}

// The examples are integration tests for the Gradle plugin rather than part of the library, and
// they are most of the build: sixteen projects whose tasks are webpack bundles, browser
// distributions, wasm binary optimisation, boot and shadow jars, a Quarkus uber-jar and Micronaut
// optimised runners, against a modules graph that finishes in seconds. Twelve of them are the same
// service interface behind seven frameworks and two DI flavours.
//
// They are skipped by default so that `build` means the library. `-Pkilua.examples=true` brings
// them back for the runs that are meant to check them. Nothing is removed from the build: every
// example still configures and still resolves its dependencies, so `:examples:fullstack-ktor:check`
// compiles every one of its targets and runs the generator over the service, and it is not gated -
// which is how the single example CI keeps stays a real end to end check.
//
// The dependencies are removed rather than the task being disabled, because `enabled = false` only
// stops a task's own actions: everything it depends on still runs, so a disabled `build` left
// `assemble` and `check` - and the wasm optimiser behind them - to do the bulk of the work anyway.
//
// This is done once every project has been evaluated rather than through `configureEach`, because
// the lifecycle plugin sets `build`'s own dependencies while it is being applied - an action
// registered before that is quietly overwritten, which is what made the first attempt a no-op.
//
// The task container is walked rather than gated on the Kotlin plugin because three of the example
// subprojects apply kotlin("jvm") or a framework plugin instead of the multiplatform one, and
// their packaging is the expensive part.
val buildExamples = providers.gradleProperty("kilua.examples").map { it.toBoolean() }.getOrElse(false)

if (!buildExamples) {
    gradle.projectsEvaluated {
        subprojects.filter { it.path.startsWith(":examples") }.forEach { example ->
            example.tasks.findByName("build")?.setDependsOn(emptyList<Any>())
        }
    }
}

nmcpAggregation {
    centralPortal {
        username = findProperty("mavenCentralUsername")?.toString()
        password = findProperty("mavenCentralPassword")?.toString()
        publishingType = "USER_MANAGED"
        publicationName = "Kilua RPC $version"
    }
    allowDuplicateProjectNames.set(true)
}

dependencies {
    nmcpAggregation(project(":modules:kilua-rpc-annotations"))
    nmcpAggregation(project(":modules:kilua-rpc-types"))
    nmcpAggregation(project(":modules:kilua-rpc-core"))
    nmcpAggregation(project(":modules:kilua-rpc-javalin"))
    nmcpAggregation(project(":modules:kilua-rpc-javalin-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-javalin-metro"))
    nmcpAggregation(project(":modules:kilua-rpc-jooby"))
    nmcpAggregation(project(":modules:kilua-rpc-jooby-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-jooby-metro"))
    nmcpAggregation(project(":modules:kilua-rpc-ktor"))
    nmcpAggregation(project(":modules:kilua-rpc-ktor-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-ktor-metro"))
    nmcpAggregation(project(":modules:kilua-rpc-metro"))
    nmcpAggregation(project(":modules:kilua-rpc-micronaut"))
    nmcpAggregation(project(":modules:kilua-rpc-quarkus"))
    nmcpAggregation(project(":modules:kilua-rpc-spring-boot"))
    nmcpAggregation(project(":modules:kilua-rpc-vertx"))
    nmcpAggregation(project(":modules:kilua-rpc-vertx-koin"))
    nmcpAggregation(project(":modules:kilua-rpc-vertx-metro"))
    nmcpAggregation(project(":plugins:kilua-rpc-gradle-plugin"))
    nmcpAggregation(project(":plugins:kilua-rpc-ksp-processor"))
    dokka(project(":modules:kilua-rpc-annotations"))
    dokka(project(":modules:kilua-rpc-core"))
    dokka(project(":modules:kilua-rpc-javalin"))
    dokka(project(":modules:kilua-rpc-javalin-koin"))
    dokka(project(":modules:kilua-rpc-javalin-metro"))
    dokka(project(":modules:kilua-rpc-jooby"))
    dokka(project(":modules:kilua-rpc-jooby-koin"))
    dokka(project(":modules:kilua-rpc-jooby-metro"))
    dokka(project(":modules:kilua-rpc-ktor"))
    dokka(project(":modules:kilua-rpc-ktor-koin"))
    dokka(project(":modules:kilua-rpc-ktor-metro"))
    dokka(project(":modules:kilua-rpc-metro"))
    dokka(project(":modules:kilua-rpc-micronaut"))
    dokka(project(":modules:kilua-rpc-quarkus"))
    dokka(project(":modules:kilua-rpc-spring-boot"))
    dokka(project(":modules:kilua-rpc-vertx"))
    dokka(project(":modules:kilua-rpc-vertx-koin"))
    dokka(project(":modules:kilua-rpc-vertx-metro"))
    dokka(project(":modules:kilua-rpc-types"))
}
