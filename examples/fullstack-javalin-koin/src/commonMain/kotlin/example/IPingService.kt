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

package example

import dev.kilua.rpc.AbstractServiceException
import dev.kilua.rpc.RemoteData
import dev.kilua.rpc.RemoteFilter
import dev.kilua.rpc.RemoteSorter
import dev.kilua.rpc.annotations.RpcService
import dev.kilua.rpc.annotations.RpcServiceException
import dev.kilua.rpc.types.Decimal
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@RpcServiceException
class MyFirstException(override val message: String) : AbstractServiceException()

@RpcServiceException
class MySecondException(override val message: String) : AbstractServiceException()

@Serializable
data class MyData(val id: Int, val name: String)

@RpcService
interface IPingService {
    suspend fun ping(message: String? = null): String
    suspend fun getData(id: Int, name: String): MyData
    suspend fun getDataResult(id: Int, name: String): Result<MyData>

    suspend fun kiluaTypes(
        files: List<MyData>,
        localDate: LocalDate,
        localTime: LocalTime,
        localDateTime: LocalDateTime,
        decimal: Decimal
    ): Result<List<LocalDate>>

    suspend fun wservice(input: ReceiveChannel<Int>, output: SendChannel<String>) {}
    suspend fun wservice(handler: suspend (SendChannel<Int>, ReceiveChannel<String>) -> Unit) {}

    suspend fun sseConnection(output: SendChannel<String>) {}
    suspend fun sseConnection(handler: suspend (ReceiveChannel<String>) -> Unit) {}

    suspend fun rowData(
        page: Int?,
        size: Int?,
        filter: List<RemoteFilter>?,
        sorter: List<RemoteSorter>?,
        state: String?
    ): RemoteData<MyData>
}
