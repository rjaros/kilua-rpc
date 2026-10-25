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
 * Gives the android and Kotlin/Native compilations of this module something to compile.
 *
 * Every declaration here is jvm-only dependency injection wiring, so there is nothing to compile for
 * a platform that is not a JVM one - and the variants are still published, because a consumer with an
 * android or a native target cannot resolve this module without them.
 *
 * Kotlin only writes a klib or an aar for a compilation that has at least one source file, though.
 * With an empty source set the compile task is NO-SOURCE, the artifact is never produced, and
 * publishing fails outright - which turns "an artifact that carries no code" into "no artifact at
 * all" and fails every consumer on those platforms over it.
 *
 * This declaration is therefore the whole reason those artifacts exist. It is internal, so it is not
 * part of the published API, and it is deliberately referenced from nowhere.
 */
internal val kiluaRpcEmptyVariantMarker: Int = 0
