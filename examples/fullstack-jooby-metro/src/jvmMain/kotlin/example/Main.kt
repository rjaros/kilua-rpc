package example

import dev.kilua.rpc.RpcGraph
import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpcMetro
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.jooby.kt.runApp

@DependencyGraph(AppScope::class)
interface AppGraph : RpcGraph.Factory

fun main(args: Array<String>) {
    runApp(args) {
        initRpcMetro(createGraph<AppGraph>())
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
