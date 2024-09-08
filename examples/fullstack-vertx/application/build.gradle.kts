plugins {
    kotlin("jvm")
    alias(libs.plugins.vertx)
}

dependencies {
    implementation(project(":examples:fullstack-vertx"))
}

vertx {
    mainVerticle = project.parent?.extra?.get("mainClassName")?.toString()!!
    watch = listOf("../src")
}
