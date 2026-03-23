package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import io.javalin.Javalin
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan
class PingModule

fun main() {
    Javalin.create { config ->
        config.initRpc {
            modules(PingModule().module())
        }
        getAllServiceManagers().forEach { config.applyRoutes(it) }
    }.start(8080)
}
