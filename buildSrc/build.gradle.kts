plugins {
    kotlin("jvm") version libs.versions.kotlin
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
    // Puts the Android Gradle plugin on the build script classpath of every project, which is what
    // the android target of a multiplatform module and the hierarchy helper that groups it are
    // built on. It is applied by id, without a version, because a versioned request would clash
    // with the copy coming from here.
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation(gradleApi())
}
