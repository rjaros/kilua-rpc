package example

import dev.kilua.rpc.applyRoutes
import dev.kilua.rpc.getAllServiceManagers
import dev.kilua.rpc.initRpc
import io.javalin.Javalin
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module
@ComponentScan
class PingModule

fun main() {
    Javalin.create().start(8080).apply {
        initRpc(PingModule().module)
        getAllServiceManagers().forEach { applyRoutes(it) }
    }
}
