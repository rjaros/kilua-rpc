package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpcKoin
import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.plugin.module.dsl.module

@Module
@ComponentScan
class PingModule

class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val server = vertx.createHttpServer()
        vertx.initRpcKoin(router, server, getAllServiceManagers(), null) {
            module<PingModule>()
        }
        getAllServiceManagers().forEach {
            vertx.applyRoutes(router, it)
        }
        server.requestHandler(router).listen(8080)
    }
}
