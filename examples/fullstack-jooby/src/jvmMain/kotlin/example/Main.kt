package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import io.jooby.kt.runApp

fun main(args: Array<String>) {
    runApp(args) {
        initRpc()
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
