package example

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class PingService : IPingService {

    override suspend fun ping(message: String?): String {
        println(message)
        return "Hello world from server!"
    }

    override suspend fun sseConnection(output: SendChannel<String>) {
        var i = 0
        while (true) {
            output.send("Hello world (${i++})!")
            delay(3.seconds)
        }
    }
}
