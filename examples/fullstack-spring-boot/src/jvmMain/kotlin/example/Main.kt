package example

import dev.kilua.rpc.getAllServiceManagers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(
    exclude = [
        org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration::class,
        org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration::class,
    ]
)
class RpcApplication {
    @Bean
    fun getManagers() = getAllServiceManagers()
}

fun main(args: Array<String>) {
    runApplication<RpcApplication>(*args)
}
