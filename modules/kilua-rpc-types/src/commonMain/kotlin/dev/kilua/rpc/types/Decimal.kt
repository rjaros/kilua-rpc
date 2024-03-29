/*
 * Copyright (c) 2024 Robert Jaros
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
package dev.kilua.rpc.types

import dev.kilua.rpc.types.serializers.DecimalSerializer
import kotlinx.serialization.Serializable

/**
 * A serializable decimal number type.
 */
public typealias Decimal = @Serializable(with = DecimalSerializer::class) InternalDecimal

/**
 * A helper extension function to convert Double to Decimal number type.
 */
public expect fun Double.toDecimal(): Decimal

/**
 * A helper extension function to convert Int to Decimal number type.
 */
public expect fun Int.toDecimal(): Decimal

/**
 * A helper extension function to convert Decimal number type to Double.
 */
public expect fun Decimal.toDouble(): Double

/**
 * A helper extension function to convert Decimal number type to Int.
 */
public expect fun Decimal.toInt(): Int
