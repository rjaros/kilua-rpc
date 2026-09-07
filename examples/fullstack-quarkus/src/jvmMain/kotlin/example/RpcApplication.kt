package example

import dev.kilua.rpc.RpcManagers
import dev.kilua.rpc.getAllServiceManagers
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

@ApplicationScoped
class RpcApplication {
    @Produces
    @ApplicationScoped
    fun getManagers() = RpcManagers(getAllServiceManagers())
}
