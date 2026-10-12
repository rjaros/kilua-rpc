plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kilua.rpc)
}

// A project with a single platform target, and the smallest one that can exist: no JVM target, so
// no server distribution, no web target, no metadata compilation.
//
// The generator normally splits its output into a common half and one half per platform. With a
// single target there is no metadata KSP task to produce the common half, so the plugin tells the
// processor to emit a self-contained platform half instead: managers, client, lookups and the
// exceptions registration all land in linuxX64Main. The consequence is that the generated functions
// are visible only from that source set - every call below is in linuxX64Main on purpose, because a
// call from commonMain would resolve to the library placeholder and throw at runtime.
kotlin {
    jvmToolchain(25)
    linuxX64 {
        binaries {
            executable {
                entryPoint = "example.main"
            }
        }
    }
    sourceSets {
        getByName("commonMain") {
            dependencies {
                // The service interface and the generated manager are plain Kotlin, but the manager
                // extends RpcServiceManager, which is declared in the server modules. A client
                // therefore depends on one of them even though it has no server of its own; the
                // native variant brings the shared Ktor client transport.
                implementation(project(":modules:kilua-rpc-ktor"))
            }
        }
    }
}
