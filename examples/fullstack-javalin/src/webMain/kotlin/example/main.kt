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

import dev.kilua.rpc.getService
import dev.kilua.rpc.types.toDecimal
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

fun main() {

    val pingService = getService<IPingService>()

    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch {
        val value = pingService.ping("Hello world from client!")
        println(value)
        try {
            val data = pingService.getData(-1, "")
            println(data.toString())
        } catch (e: MyFirstException) {
            println("MyFirstException: ${e.message}")
        } catch (e: MySecondException) {
            println("MySecondException: ${e.message}")
        } catch (e: Exception) {
            println("Exception: ${e.message}")
        }
        val result = pingService.getDataResult(2, "")
        result.fold(
            onSuccess = { println("Success: $it") },
            onFailure = {
                when (it) {
                    is MyFirstException -> println("MyFirstException: ${it.message}")
                    is MySecondException -> println("MySecondException: ${it.message}")
                    else -> println("Error: $it")
                }
            }
        )
        val kiluaTypes = pingService.kiluaTypes(
            listOf(MyData(1, "name")),
            LocalDate(2023, 1, 1),
            LocalTime(12, 0),
            LocalDateTime(2023, 1, 1, 12, 0),
            1.0.toDecimal()
        )
        println("KiluaTypes: $kiluaTypes")
        launch {
            pingService.wservice { sendChannel, receiveChannel ->
                coroutineScope {
                    launch {
                        sendChannel.send(1)
                        sendChannel.send(2)
                    }
                    launch {
                        for (input in receiveChannel) {
                            println(input)
                        }
                    }
                }
            }
        }
        launch {
            pingService.sseConnection { receiveChannel ->
                coroutineScope {
                    launch {
                        for (input in receiveChannel) {
                            println(input)
                        }
                    }
                }
            }
        }
    }
}
