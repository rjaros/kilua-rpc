package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import dev.kilua.rpc.registerService
import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router

class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val server = vertx.createHttpServer()
        vertx.initRpc(router, server, getAllServiceManagers()) {
            registerService<IPingService> { PingService() }
        }
        getAllServiceManagers().forEach {
            vertx.applyRoutes(router, it)
        }
        server.requestHandler(router).listen(8080)
    }
}
