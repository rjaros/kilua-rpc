plugins {
    kotlin("jvm")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":examples:fullstack-spring-boot"))
    implementation("org.springframework.boot:spring-boot-devtools")
}

springBoot {
    mainClass.value(project.parent?.extra?.get("mainClassName")?.toString())
}
