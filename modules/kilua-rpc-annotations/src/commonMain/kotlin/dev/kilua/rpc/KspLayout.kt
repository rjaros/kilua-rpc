/*
 * Copyright (c) 2026 Robert Jaros
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.kilua.rpc

/**
 * Where the Kilua RPC KSP processor writes its generated files, and how the Gradle plugin finds them
 * again.
 *
 * Both sides of that deal are published as separate artifacts and only agree by convention, so the
 * convention is declared once here rather than spelled out independently in each of them. A missed
 * update then fails to compile instead of silently producing code that no compilation ever sees.
 *
 * This lives in the annotations module only because that is the one library both the plugin and the
 * processor already depend on; nothing here concerns the annotations themselves.
 */
public object KspLayout {

    /**
     * Root of everything the processor writes, relative to the project build directory.
     */
    public const val ROOT_DIR: String = "build/generated/ksp"

    /**
     * Source sets the processor emits code into, keyed by source set name, each mapped to the
     * directory its code goes into relative to [ROOT_DIR].
     *
     * `androidMain` is deliberately absent: for the android target contributed by AGP, KSP registers
     * its own output with the compilation, so the plugin has nothing to add.
     */
    public val outputDirs: Map<String, String> = mapOf(
        "commonMain" to "metadata/commonMain",
        "jsMain" to "js/jsMain",
        "wasmJsMain" to "wasmJs/wasmJsMain",
        "jvmMain" to "jvm/jvmMain",
    )

    /**
     * [outputDirs] rendered as source directory paths a source set can be pointed at, keyed by source
     * set name.
     */
    public val sourceDirs: Map<String, String> =
        outputDirs.mapValues { [_, relative] -> "$ROOT_DIR/$relative/kotlin" }

    /**
     * The source set a file the processor wrote belongs to, or null when it belongs to none of them.
     *
     * The path is inspected for [ROOT_DIR] rather than assumed to start with it, and the source set
     * is the segment after the target name - so `.../ksp/metadata/commonMain/kotlin/x/y.kt` is
     * `commonMain` while `.../ksp/android/androidMain/kotlin/x/y.kt` is `androidMain`.
     */
    public fun sourceSetOf(path: String): String? {
        val normalized = path.replace('\\', '/')
        val marker = "$ROOT_DIR/"
        val start = normalized.indexOf(marker)
        if (start < 0) return null
        val segments = normalized.substring(start + marker.length).split('/')
        return segments.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }
}
