package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpcKoin
import io.jooby.kt.runApp
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val pingModule = module {
    factoryOf(::PingService) bind IPingService::class
}

fun main(args: Array<String>) {
    runApp(args) {
        initRpcKoin {
            modules(pingModule)
        }
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
