package example

import dev.kilua.rpc.RpcGraph
import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpcMetro
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router

@DependencyGraph(AppScope::class)
interface AppGraph : RpcGraph.Factory

class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val server = vertx.createHttpServer()
        vertx.initRpcMetro(createGraph<AppGraph>(), router, server, getAllServiceManagers(), null)
        getAllServiceManagers().forEach {
            vertx.applyRoutes(router, it)
        }
        server.requestHandler(router).listen(8080)
    }
}
