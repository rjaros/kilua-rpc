package example

import dev.kilua.rpc.RpcManagers
import dev.kilua.rpc.getAllServiceManagers
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.Micronaut.run

@Factory
class RpcApplication {
    @Bean
    fun getManagers() = RpcManagers(getAllServiceManagers())
}

fun main(args: Array<String>) {
    run(*args)
}
