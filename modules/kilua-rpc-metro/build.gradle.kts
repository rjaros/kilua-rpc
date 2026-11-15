plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.nmcp)
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    id("de.infix.testBalloon")
}

kotlin {
    explicitApi()
    compilerOptions()
    kotlinJvmTargets()
}

setupDokka()
setupPublishing()
