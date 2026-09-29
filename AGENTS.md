# AGENTS Notes

- Use `./gradlew` (wrapper is Gradle 9.8.0).
- This repo is a Gradle multi-project: `modules/` (published runtime libs), `plugins/` (Gradle plugin + KSP processor), `examples/` (integration/demo apps).
- Most modules/examples build with `jvmToolchain(25)`; plugin/KSP projects target JVM 21.

## Critical dependency gotcha

- Examples do **not** depend on local `project(...)` modules; they consume published Maven coords from `libs.versions.toml` (`versions.kilua-rpc-published`). Read the current value from the catalog rather than assuming one — it is bumped per release.
- After changing library/plugin code, publish fresh local artifacts before validating examples: `./gradlew publishToMavenLocal -PSNAPSHOT=true` (or `./build-mvnlocal.sh`).
- If example behavior looks stale, clear/rebuild local Maven artifacts first; `mavenLocal()` is enabled in settings.

## Codegen and wiring

- `@RpcService` and `@RpcServiceException` are processed by `plugins/kilua-rpc-ksp-processor`; generated code lands under `build/generated/ksp/...` (never edit generated files).
- Placeholder APIs intentionally throw until generated (`modules/kilua-rpc-core/src/commonMain/kotlin/dev/kilua/rpc/RpcService.kt`, `modules/kilua-rpc-ktor/src/commonMain/kotlin/dev/kilua/rpc/RpcFunctions.kt`); failures there usually mean KSP/plugin wiring is broken.
- Kilua plugin adds generated source dirs and packaging tasks; configurable properties are `dev.kilua.rpc.plugin.enableGradleTasks` and `dev.kilua.rpc.plugin.jarArchiveFileName`.

## Android / KMP targets

- `modules/kilua-rpc-core` and `modules/kilua-rpc-types` publish an Android target through `com.android.kotlin.multiplatform.library`. AGP 9 rejects the old `com.android.library` alongside `kotlin("multiplatform")` — the KMP-specific plugin is mandatory.
- AGP lives on the buildSrc classpath (`com.android.tools.build:gradle` in `buildSrc/build.gradle.kts`) and is applied **versionless** in module build scripts, so the root build declares no plugin alias. `google()` is required in both `pluginManagement` and `dependencyResolutionManagement` in `settings.gradle.kts`.
- The hierarchy comes from `kiluaHierarchyTemplate()` in `buildSrc/src/main/kotlin/Shared.kt`: `jvmMain` and `androidMain` both depend on `jvmAndAndroidMain`, which depends on `commonMain`. KMP's `withAndroidTarget()` does **not** match AGP's target — the helper uses `com.android.build.api.withAndroid()`.
- JVM-only server sources (`RpcServiceBinder`, `SseHandler`, `WebsocketHandler`, `RouteMapRegistry`, …) stay in `jvmMain`. Android gets the client only, over Ktor CIO.
- The Android target has a single variant: the compile task is `compileAndroidMain`. There is no `compileDebugKotlinAndroid` and no `testDebugUnitTest`.
- Android SDK: `ANDROID_HOME` / `ANDROID_SDK_ROOT` point at `/home/rjaros/Programs/Android/Sdk`, with `platform-tools`, `platforms;android-37.0` and `build-tools;37.0.0` installed. Compile/min SDK and the JVM target are `androidCompileSdk = 37`, `androidMinSdk = 21`, `androidJvmTarget = JVM_11`.

## Source-set gotchas

- A file name that exists in both `commonMain` and a JVM-consumed shared or leaf source set collides, because both generate the same `<Name>Kt` facade — `Duplicate JVM class name 'dev/kilua/rpc/LoggerKt'`. Platform actuals need a suffix: `Decimal.jvmAndAndroid.kt`, `RpcService.jvmAndAndroid.kt`, `Logger.jvm.kt`, `Logger.android.kt`. This is why the pre-existing `Decimal.jvm.kt` was already suffixed.
- The shared source set is lowercase-`j` `jvmAndAndroidMain`.
- Shared platform URL helpers live in `jvmAndAndroidMain/.../PlatformUtils.kt` (not `Utils.kt`) precisely so they do not clash with the multiplatform `commonMain/.../Utils.kt`.

## Runtime conventions that affect debugging

- Default route prefixes are fixed in binder code: HTTP `/rpc/`, WebSocket `/rpcws/`, SSE `/rpcsse/`.
- Ktor default static SPA resources are mounted from `/assets`; plugin packaging uses `/public` for Micronaut/Spring Boot and `/webroot` for Vert.x.
- The client logs through Ktor's `io.ktor.client.plugins.logging.Logger` (`api(libs.ktor.client.logging)` in the `jvmAndAndroidMain` source set of `kilua-rpc-core`). The per-platform actual lives in `Logger.jvm.kt` (`Logger.DEFAULT`, SLF4J-backed) and `Logger.android.kt` (`Logger.ANDROID`, `android.util.Log`). SLF4J only arrives transitively and no binding ships, so JVM consumers still have to add one (e.g. Logback); the core `jvmTest` source set adds `runtimeOnly(libs.logback.classic)`.
- `Logger` takes only `log(String)` — no throwable overload. Agent failures therefore go through the internal `Logger.logError` helper, which appends the stack trace to the message. Note also that `Logger.DEFAULT`/`Logger.ANDROID` are *extension properties on the companion*, so they need explicit imports.

## testBalloon gotchas (JVM tests)

- `./gradlew :modules:kilua-rpc-core:jvmTest --tests "dev.kilua.rpc.SomeSpec"` does **not** match testBalloon suites; the filter is ignored. Run the whole task and read `build/test-results/jvmTest/*.xml`. The same applies to `testAndroidHostTest` / `build/test-results/testAndroidHostTest/*.xml`.
- `testAndroidHostTest` runs only the `commonTest` suites (currently `ResultSerializerSpec`, 4 tests). The source tree is `androidHostTest → jvmAndAndroidTest → commonTest`, so the JVM-only suites in `src/jvmTest` are never picked up — that is by design, since they bind real Ktor server sockets.
- On Android, results are reported under classname `de.infix.testBalloon.framework.shared.internal.entryPoint.TestBalloonJUnit4` regardless of which suite actually ran. Do not read that classname as "these are testBalloon's own self-tests" — check the individual test names instead.
- testBalloon runs each test in a `kotlinx.coroutines.test.TestScope`, so `delay` and `withTimeout` use *virtual* time and fire instantly for code that waits on a real socket. Socket-based tests must escape to a real clock, e.g. `runBlocking { withTimeout(60.seconds) { withContext(Dispatchers.Default) { ... } } }`.
- Never let a test hang on teardown: a client that keeps its connection pool alive can block `embeddedServer.stop(...)` indefinitely. Run `stop` on a daemon thread and bound the wait.

## High-value commands

- Fast library verification: `./gradlew :modules:kilua-rpc-core:jvmTest`.
- All core targets at once: `./gradlew :modules:kilua-rpc-core:jvmTest :modules:kilua-rpc-core:testAndroidHostTest :modules:kilua-rpc-core:compileKotlinJs :modules:kilua-rpc-core:compileKotlinWasmJs :modules:kilua-rpc-core:compileAndroidMain` (expect 64 JVM + 4 Android tests, 0 failures).
- Browser target tests (Karma + ChromeHeadless required): `./gradlew :modules:kilua-rpc-core:jsBrowserTest :modules:kilua-rpc-core:wasmJsBrowserTest`.
- Only `modules/kilua-rpc-core` currently contains test sources (`src/commonTest`, `src/jvmTest`, `src/webTest`); there are no `jvmAndAndroidTest`/`androidHostTest` source dirs, so the Android suite set is whatever sits in `commonTest`.
- Fullstack example packaging tasks come from the Kilua plugin: `:examples:<name>:jarWithJs`, `:examples:<name>:jarWithWasmJs`.
- TypeScript example flow (`examples/typescript-ktor`):
  1) `./gradlew :examples:typescript-ktor:ktor-server:jsBrowserDevelopmentLibraryDistribution`
  2) `npm install` in `examples/typescript-ktor/website`
  3) `npx vite`
  4) backend: `./gradlew :examples:typescript-ktor:ktor-server:jvmRun`

## What not to assume

- No repo lint setup like ktlint/detekt/spotless is configured; do not invent lint commands in validation steps.
- Web deliberately keeps its **own** `RpcAgent`/`CallAgent` implementation in `webMain`, separate from the `jvmAndAndroidMain` one. Consolidating them is intentional future work, not an oversight — do not "fix" the duplication unasked, and expect the error-mapping behavior (`ServiceException` reconstruction, status mapping, response-id checks) to be maintained in both copies by hand.
