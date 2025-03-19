package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import dev.kilua.rpc.registerService
import io.jooby.kt.runApp

fun main(args: Array<String>) {
    runApp(args) {
        initRpc {
            registerService<IPingService> { PingService() }
        }
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
