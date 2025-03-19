package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import dev.kilua.rpc.registerService
import io.javalin.Javalin

fun main() {
    Javalin.create().start(8080).apply {
        initRpc {
            registerService<IPingService> { PingService() }
        }
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
