package example

import dev.kilua.rpc.AbstractServiceException
import dev.kilua.rpc.RemoteData
import dev.kilua.rpc.RemoteFilter
import dev.kilua.rpc.RemoteSorter
import dev.kilua.rpc.types.Decimal
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.koin.core.annotation.Factory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Factory
class PingService : IPingService {

    override suspend fun ping(message: String?): String {
        println(message)
        return "Hello world from server!"
    }

    override suspend fun getData(id: Int, name: String): MyData {
        if (id < 0) {
            throw MyFirstException("id must be positive")
        }
        if (name.isBlank()) {
            throw MySecondException("name must not be blank")
        }
        return MyData(id, name)
    }

    override suspend fun getDataResult(id: Int, name: String): Result<MyData> {
        try {
            return Result.success(getData(id, name))
        } catch (e: AbstractServiceException) {
            return Result.failure(e)
        }
    }

    override suspend fun kiluaTypes(
        files: List<MyData>,
        localDate: LocalDate,
        localTime: LocalTime,
        localDateTime: LocalDateTime,
        instant: Instant,
        decimal: Decimal
    ): Result<List<LocalDate>> {
        println(files)
        println(localDate)
        println(localTime)
        println(localDateTime)
        println(instant)
        println(decimal)
        return Result.success(listOf(localDate))
    }

    override suspend fun wservice(input: ReceiveChannel<Int>, output: SendChannel<String>) {
        for (i in input) {
            output.send("I'v got: $i")
        }
    }

    override suspend fun sseConnection(output: SendChannel<String>) {
        var i = 0
        while (true) {
            output.send("Hello world (${i++})!")
            delay(3.seconds)
        }
    }

    override suspend fun rowData(
        page: Int?,
        size: Int?,
        filter: List<RemoteFilter>?,
        sorter: List<RemoteSorter>?,
        state: String?
    ): RemoteData<MyData> {
        return RemoteData()
    }
}
