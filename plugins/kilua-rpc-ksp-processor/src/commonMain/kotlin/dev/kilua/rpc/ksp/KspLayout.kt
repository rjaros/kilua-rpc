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

package dev.kilua.rpc.ksp

/**
 * The layout of the directories the Kilua RPC KSP processor writes its generated files into, and how
 * to tell which of them a given file belongs to.
 */
internal object KspLayout {

    /**
     * Root of everything the processor writes, relative to the project build directory.
     */
    internal const val ROOT_DIR: String = "build/generated/ksp"

    /**
     * The source set a file the processor wrote belongs to, or null when it belongs to none of them.
     *
     * The path is inspected for [ROOT_DIR] rather than assumed to start with it, and the source set
     * is the segment after the target name - so `.../ksp/metadata/commonMain/kotlin/x/y.kt` is
     * `commonMain` while `.../ksp/android/androidMain/kotlin/x/y.kt` is `androidMain`.
     */
    internal fun sourceSetOf(path: String): String? {
        val normalized = path.replace('\\', '/')
        val marker = "$ROOT_DIR/"
        val start = normalized.indexOf(marker)
        if (start < 0) return null
        val segments = normalized.substring(start + marker.length).split('/')
        return segments.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }
}
