package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import io.javalin.Javalin

fun main() {
    Javalin.create().start(8080).apply {
        initRpc()
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
