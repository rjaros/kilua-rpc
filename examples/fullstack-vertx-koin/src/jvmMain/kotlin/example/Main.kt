package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module
@ComponentScan
class PingModule

class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val server = vertx.createHttpServer()
        vertx.initRpc(router, server, getAllServiceManagers(), null, PingModule().module)
        getAllServiceManagers().forEach {
            vertx.applyRoutes(router, it)
        }
        server.requestHandler(router).listen(8080)
    }
}
