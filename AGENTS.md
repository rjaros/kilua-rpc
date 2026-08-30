# AGENTS Notes

- Use `./gradlew` (wrapper is Gradle 9.7.1).
- This repo is a Gradle multi-project: `modules/` (published runtime libs), `plugins/` (Gradle plugin + KSP processor), `examples/` (integration/demo apps).
- Most modules/examples build with `jvmToolchain(25)`; plugin/KSP projects target JVM 21.

## Critical dependency gotcha

- Examples do **not** depend on local `project(...)` modules; they consume published Maven coords from `libs.versions.toml` (`versions.kilua-rpc-published`, currently `0.0.46-RC2-SNAPSHOT`).
- After changing library/plugin code, publish fresh local artifacts before validating examples: `./gradlew publishToMavenLocal -PSNAPSHOT=true` (or `./build-mvnlocal.sh`).
- If example behavior looks stale, clear/rebuild local Maven artifacts first; `mavenLocal()` is enabled in settings.

## Codegen and wiring

- `@RpcService` and `@RpcServiceException` are processed by `plugins/kilua-rpc-ksp-processor`; generated code lands under `build/generated/ksp/...` (never edit generated files).
- Placeholder APIs intentionally throw until generated (`modules/kilua-rpc-core/src/commonMain/kotlin/dev/kilua/rpc/RpcService.kt`, `modules/kilua-rpc-ktor/src/commonMain/kotlin/dev/kilua/rpc/RpcFunctions.kt`); failures there usually mean KSP/plugin wiring is broken.
- Kilua plugin adds generated source dirs and packaging tasks; configurable properties are `dev.kilua.rpc.plugin.enableGradleTasks` and `dev.kilua.rpc.plugin.jarArchiveFileName`.

## Runtime conventions that affect debugging

- Default route prefixes are fixed in binder code: HTTP `/rpc/`, WebSocket `/rpcws/`, SSE `/rpcsse/`.
- Ktor default static SPA resources are mounted from `/assets`; plugin packaging uses `/public` for Micronaut/Spring Boot and `/webroot` for Vert.x.

## High-value commands

- Fast library verification: `./gradlew :modules:kilua-rpc-core:jvmTest`.
- Run one JVM test: `./gradlew :modules:kilua-rpc-core:jvmTest --tests "dev.kilua.rpc.NameGeneratorSpec"`.
- Browser target tests (Karma + ChromeHeadless required): `./gradlew :modules:kilua-rpc-core:jsBrowserTest :modules:kilua-rpc-core:wasmJsBrowserTest`.
- Only `modules/kilua-rpc-core` currently contains test sources (`src/commonTest`, `src/jvmTest`, `src/webTest`).
- Fullstack example packaging tasks come from the Kilua plugin: `:examples:<name>:jarWithJs`, `:examples:<name>:jarWithWasmJs`.
- TypeScript example flow (`examples/typescript-ktor`):
  1) `./gradlew :examples:typescript-ktor:ktor-server:jsBrowserDevelopmentLibraryDistribution`
  2) `npm install` in `examples/typescript-ktor/website`
  3) `npx vite`
  4) backend: `./gradlew :examples:typescript-ktor:ktor-server:jvmRun`

## What not to assume

- No repo lint setup like ktlint/detekt/spotless is configured; do not invent lint commands in validation steps.
