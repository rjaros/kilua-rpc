package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpcKoin
import io.javalin.Javalin
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.plugin.module.dsl.module

@Module
@ComponentScan
class PingModule

fun main() {
    Javalin.create { config ->
        config.initRpcKoin {
            module<PingModule>()
        }
        getAllServiceManagers().forEach { config.applyRoutes(it) }
    }.start(8080)
}
