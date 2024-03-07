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

package dev.kilua.rpc

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.http.MutableHttpResponse

/**
 * A helper class for holding http response mutator function.
 */
public class HttpResponseMutator {

    internal var responseMutator: (MutableHttpResponse<String>.() -> Unit)? = null

    public fun mutate(responseMutator: MutableHttpResponse<String>.() -> Unit) {
        this.responseMutator = responseMutator
    }
}

internal object ResponseMutatorHolder {
    val threadLocalResponseMutator = ThreadLocal<HttpResponseMutator>()
}

/**
 * Helper factory for the HttpResponseMutator bean.
 */
@Factory
public open class HttpResponseMutatorBeanFactory {
    @Bean
    public open fun httpResponseMutator(): HttpResponseMutator {
        return ResponseMutatorHolder.threadLocalResponseMutator.get() ?: HttpResponseMutator()
    }
}
