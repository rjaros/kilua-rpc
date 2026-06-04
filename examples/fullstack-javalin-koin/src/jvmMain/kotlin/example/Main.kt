package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpcKoin
import io.javalin.Javalin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val pingModule = module {
    factoryOf(::PingService) bind IPingService::class
}

fun main() {
    Javalin.create { config ->
        config.initRpcKoin {
            modules(pingModule)
        }
        getAllServiceManagers().forEach { config.applyRoutes(it) }
    }.start(8080)
}
