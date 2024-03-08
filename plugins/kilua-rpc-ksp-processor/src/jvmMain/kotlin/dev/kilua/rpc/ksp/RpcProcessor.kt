/*
 * Copyright (c) 2024 Robert Jaros
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.kilua.rpc.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import dev.kilua.rpc.annotations.RpcBinding
import dev.kilua.rpc.annotations.RpcBindingMethod
import dev.kilua.rpc.annotations.RpcBindingRoute
import dev.kilua.rpc.annotations.RpcService
import dev.kilua.rpc.annotations.RpcServiceException
import java.io.File

public data class NameDetails(
    val packageName: String,
    val className: String,
    val interfaceName: String,
    val managerName: String
)

public data class ExceptionNameDetails(val packageName: String, val className: String)

@OptIn(KspExperimental::class)
public class RpcProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    private var isInitialInvocation = true

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (!isInitialInvocation) {
            // A subsequent invocation is for processing generated files. We do not need to process these.
            return emptyList()
        }
        isInitialInvocation = false
        val services = mutableListOf<NameDetails>()
        val deps = resolver.getSymbolsWithAnnotation(RpcService::class.qualifiedName.orEmpty())
            .filterIsInstance<KSClassDeclaration>().filter(KSNode::validate)
            .filter { it.classKind == ClassKind.INTERFACE }
            .mapNotNull { classDeclaration ->
                val interfaceName = classDeclaration.simpleName.asString()
                val isOldConvention = interfaceName.startsWith("I") && interfaceName.endsWith("Service")
                val packageName = classDeclaration.packageName.asString()
                val className = if (isOldConvention) interfaceName.drop(1) else "${interfaceName}Impl"
                val managerName = if (isOldConvention) "${className}Manager" else "${interfaceName}Manager"
                val dependencies = classDeclaration.containingFile?.let { Dependencies(true, it) } ?: Dependencies(true)
                codeGenerator.createNewFile(dependencies, packageName, className).writer().use {
                    when (codeGenerator.generatedFile.first().toString().sourceSetBelow("ksp")) {
                        "commonMain" -> {
                            it.write(
                                generateCommonCode(
                                    packageName,
                                    className,
                                    interfaceName,
                                    managerName,
                                    classDeclaration
                                )
                            )
                        }

                        "jsMain" -> {
                            it.write(
                                generateFrontendCode(
                                    packageName,
                                    className,
                                    interfaceName,
                                    managerName,
                                    classDeclaration
                                )
                            )
                        }

                        "wasmJsMain" -> {
                            it.write(
                                generateFrontendCode(
                                    packageName,
                                    className,
                                    interfaceName,
                                    managerName,
                                    classDeclaration
                                )
                            )
                        }
                    }
                }
                services.add(NameDetails(packageName, className, interfaceName, managerName))
                classDeclaration.containingFile
            }.toList().toTypedArray()
        codeGenerator.createNewFile(Dependencies(true, *deps), "dev.kilua.rpc", "GeneratedRpcServiceManager")
            .writer().use {
                when (codeGenerator.generatedFile.first().toString().sourceSetBelow("ksp")) {
                    "commonMain" -> {
                        it.write(generateCommonCodeFunctions(services))
                    }

                    "jsMain" -> {
                        it.write(generateFrontendCodeFunctions(services))
                    }

                    "wasmJsMain" -> {
                        it.write(generateFrontendCodeFunctions(services))
                    }
                }
            }
        codeGenerator.createNewFile(Dependencies(true, *deps), "dev.kilua.rpc", "GeneratedRpcServiceManagerJvm")
            .writer().use {
                when (codeGenerator.generatedFile.first().toString().sourceSetBelow("ksp")) {
                    "jvmMain" -> {
                        it.write(generateJvmCodeFunctions())
                    }
                }
            }
        val exceptions = mutableListOf<ExceptionNameDetails>()
        val depsExceptions = resolver.getSymbolsWithAnnotation(RpcServiceException::class.qualifiedName.orEmpty())
            .filterIsInstance<KSClassDeclaration>().filter(KSNode::validate)
            .filter { it.classKind == ClassKind.CLASS }
            .mapNotNull { classDeclaration ->
                val className = classDeclaration.simpleName.asString()
                val packageName = classDeclaration.packageName.asString()
                exceptions.add(ExceptionNameDetails(packageName, className))
                classDeclaration.containingFile
            }.toList().toTypedArray()
        codeGenerator.createNewFile(
            Dependencies(true, *depsExceptions),
            "dev.kilua.rpc",
            "GeneratedRpcServiceExceptions"
        ).writer().use {
            when (codeGenerator.generatedFile.first().toString().sourceSetBelow("ksp")) {
                "commonMain" -> {
                    it.write(generateCommonCodeExceptions(exceptions))
                }
            }
        }
        return emptyList()
    }

    private fun String.sourceSetBelow(startDirectoryName: String): String =
        substringAfter("${File.separator}$startDirectoryName${File.separator}").substringBefore("${File.separator}kotlin${File.separator}")
            .substringAfterLast(File.separatorChar)

    private fun generateCommonCode(
        packageName: String,
        className: String,
        interfaceName: String,
        managerName: String,
        ksClassDeclaration: KSClassDeclaration
    ): String {
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            appendLine("package $packageName")
            appendLine()
            appendLine("import dev.kilua.rpc.HttpMethod")
            appendLine("import dev.kilua.rpc.RpcServiceManager")
            appendLine("import dev.kilua.rpc.registerRpcServiceExceptions")
            val types = getTypes(ksClassDeclaration.getDeclaredFunctions())
            types.sorted().forEach {
                appendLine("import $it")
            }
            if (types.contains("kotlinx.coroutines.channels.SendChannel")
                && !types.contains("kotlinx.coroutines.channels.ReceiveChannel")
            ) {
                appendLine("import kotlinx.coroutines.channels.ReceiveChannel")
            }
            appendLine()
            appendLine("expect class $className : $interfaceName {")
            val wsMethodsForClass = mutableListOf<String>()
            val sseMethodsForClass = mutableListOf<String>()
            ksClassDeclaration.getDeclaredFunctions().forEach {
                val name = it.simpleName.asString()
                val params = it.parameters
                val wsMethod =
                    if (params.size == 2)
                        params.first().type.toString().startsWith("ReceiveChannel")
                    else false
                val sseMethod =
                    if (params.size == 1)
                        params.first().type.toString().startsWith("SendChannel")
                    else false
                if (!wsMethod && !sseMethod) {
                    if (!wsMethodsForClass.contains(name) && !sseMethodsForClass.contains(name)) {
                        appendLine("    override suspend fun $name(${getParameterList(params)}): ${getTypeString(it.returnType?.resolve())}")
                    }
                } else if (wsMethod) {
                    appendLine("    override suspend fun $name(${getParameterList(params)}): Unit")
                    val type1 = getTypeString(params[0].type.resolve()).replace("ReceiveChannel", "SendChannel")
                    val type2 = getTypeString(params[1].type.resolve()).replace("SendChannel", "ReceiveChannel")
                    appendLine("    override suspend fun $name(handler: suspend ($type1, $type2) -> Unit): Unit")
                } else {
                    appendLine("    override suspend fun $name(${getParameterList(params)}): Unit")
                    val type = getTypeString(params[0].type.resolve()).replace("SendChannel", "ReceiveChannel")
                    appendLine("    override suspend fun $name(handler: suspend ($type) -> Unit): Unit")
                }
                if (wsMethod) wsMethodsForClass.add(name)
                if (sseMethod) sseMethodsForClass.add(name)
            }
            appendLine("}")
            appendLine()
            appendLine("object $managerName : RpcServiceManager<$className>($className::class) {")
            appendLine("    init {")
            appendLine("        registerRpcServiceExceptions()")
            val wsMethodsForMgr = mutableListOf<String>()
            val sseMethodsForMgr = mutableListOf<String>()
            ksClassDeclaration.getDeclaredFunctions().forEach {
                val params = it.parameters
                val wsMethod =
                    if (params.size == 2)
                        params.first().type.toString().startsWith("ReceiveChannel")
                    else false
                val sseMethod =
                    if (params.size == 1)
                        params.first().type.toString().startsWith("SendChannel")
                    else false
                val rpcBinding = it.getAnnotationsByType(RpcBinding::class).firstOrNull()
                val rpcBindingMethod = it.getAnnotationsByType(RpcBindingMethod::class).firstOrNull()
                val rpcBindingRoute = it.getAnnotationsByType(RpcBindingRoute::class).firstOrNull()
                val (method, route) = if (rpcBinding != null) {
                    val method = rpcBinding.method.name
                    val route = rpcBinding.route
                    "HttpMethod.$method" to "\"$route\""
                } else if (rpcBindingMethod != null) {
                    val method = rpcBindingMethod.method.name
                    "HttpMethod.$method" to null
                } else if (rpcBindingRoute != null) {
                    val route = rpcBindingRoute.route
                    "HttpMethod.POST" to "\"$route\""
                } else {
                    "HttpMethod.POST" to null
                }
                when {
                    it.returnType.toString().startsWith("RemoteData") ->
                        appendLine("        bindRemoteData($interfaceName::${it.simpleName.asString()}, $route)")

                    wsMethod -> if (route == null) {
                        appendLine("        bind($interfaceName::${it.simpleName.asString()}, null as String?)")
                    } else {
                        appendLine("        bind($interfaceName::${it.simpleName.asString()}, $route)")
                    }

                    sseMethod -> if (route == null) {
                        appendLine("        bind($interfaceName::${it.simpleName.asString()}, null as String?)")
                    } else {
                        appendLine("        bind($interfaceName::${it.simpleName.asString()}, $route)")
                    }

                    else -> if (!wsMethodsForMgr.contains(it.simpleName.asString()) && !sseMethodsForMgr.contains(it.simpleName.asString()))
                        appendLine("        bind($interfaceName::${it.simpleName.asString()}, $method, $route)")
                }
                if (wsMethod) wsMethodsForMgr.add(it.simpleName.asString())
                if (sseMethod) sseMethodsForMgr.add(it.simpleName.asString())
            }
            appendLine("    }")
            appendLine("}")
        }.toString()
    }

    private fun generateFrontendCode(
        packageName: String,
        className: String,
        interfaceName: String,
        managerName: String,
        ksClassDeclaration: KSClassDeclaration
    ): String {
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            appendLine("package $packageName")
            appendLine()
            appendLine("import org.w3c.fetch.RequestInit")
            appendLine("import dev.kilua.rpc.RpcAgent")
            appendLine("import kotlinx.serialization.modules.SerializersModule")
            val types = getTypes(ksClassDeclaration.getDeclaredFunctions())
            types.sorted().forEach {
                appendLine("import $it")
            }
            if (types.contains("kotlinx.coroutines.channels.SendChannel")
                && !types.contains("kotlinx.coroutines.channels.ReceiveChannel")
            ) {
                appendLine("import kotlinx.coroutines.channels.ReceiveChannel")
            }
            appendLine()
            appendLine("actual class $className(serializersModules: List<SerializersModule>? = null, requestFilter: (suspend RequestInit.() -> Unit)? = null) : $interfaceName, RpcAgent<$className>($managerName, serializersModules, requestFilter) {")
            val wsMethods = mutableListOf<String>()
            val sseMethods = mutableListOf<String>()
            ksClassDeclaration.getDeclaredFunctions().forEach {
                val name = it.simpleName.asString()
                val params = it.parameters
                val wsMethod =
                    if (params.size == 2)
                        params.first().type.toString().startsWith("ReceiveChannel")
                    else false
                val sseMethod =
                    if (params.size == 1)
                        params.first().type.toString().startsWith("SendChannel")
                    else false
                if (!wsMethod && !sseMethod) {
                    if (!wsMethods.contains(name) && !sseMethods.contains(name)) {
                        if (params.isNotEmpty()) {
                            when {
                                it.returnType.toString().startsWith("RemoteData") -> appendLine(
                                    "    actual override suspend fun $name(${
                                        getParameterList(
                                            params
                                        )
                                    }) = ${getTypeString(it.returnType!!.resolve())}()"
                                )

                                else -> appendLine(
                                    "    actual override suspend fun $name(${getParameterList(params)}) = call($interfaceName::$name, ${
                                        getParameterNames(
                                            params
                                        )
                                    })"
                                )
                            }
                        } else {
                            appendLine("    actual override suspend fun $name() = call($interfaceName::$name)")
                        }
                    }
                } else if (wsMethod) {
                    appendLine("    actual override suspend fun $name(${getParameterList(params)}) {}")
                    val type1 = getTypeString(params[0].type.resolve()).replace("ReceiveChannel", "SendChannel")
                    val type2 = getTypeString(params[1].type.resolve()).replace("SendChannel", "ReceiveChannel")
                    appendLine("    actual override suspend fun $name(handler: suspend ($type1, $type2) -> Unit) = webSocket($interfaceName::$name, handler)")
                } else {
                    appendLine("    actual override suspend fun $name(${getParameterList(params)}) {}")
                    val type = getTypeString(params[0].type.resolve()).replace("SendChannel", "ReceiveChannel")
                    appendLine("    actual override suspend fun $name(handler: suspend ($type) -> Unit) = sseConnection($interfaceName::$name, handler)")
                }
                if (wsMethod) wsMethods.add(name)
                if (sseMethod) sseMethods.add(name)
            }
            appendLine("}")
        }.toString()
    }

    private fun generateCommonCodeFunctions(services: List<NameDetails>): String {
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            if (services.isNotEmpty()) {
                appendLine("package dev.kilua.rpc")
                appendLine()
                appendLine("import kotlin.reflect.KClass")
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine()
                appendLine("@Suppress(\"UNCHECKED_CAST\")")
                appendLine("inline fun <reified T : Any> getServiceManager(): RpcServiceManager<T> = when (T::class) {")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.managerName} as RpcServiceManager<T>")
                }
                appendLine("    else -> throw IllegalArgumentException(\"Unknown service \${T::class}\")")
                appendLine("}")
                appendLine()
                appendLine("fun getAllServiceManagers(): List<RpcServiceManager<*>> = listOf(")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.managerName},")
                }
                appendLine(")")
                appendLine()
                appendLine("fun getServiceManagers(vararg kclass: KClass<*>): List<RpcServiceManager<*>> {")
                appendLine("    return kclass.map {")
                appendLine("        when (it) {")
                services.forEach {
                    appendLine("            ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.managerName}")
                }
                appendLine("            else -> throw IllegalArgumentException(\"Unknown service \${it.simpleName}\")")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("public expect inline fun <reified T : Any> getService(")
                appendLine("    serializersModules: List<SerializersModule>? = null")
                appendLine("): T")
            }
            appendLine()
        }.toString()
    }

    private fun generateFrontendCodeFunctions(services: List<NameDetails>): String {
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            if (services.isNotEmpty()) {
                appendLine("package dev.kilua.rpc")
                appendLine()
                appendLine("import org.w3c.fetch.RequestInit")
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine()
                appendLine("inline fun <reified T : Any> getService(serializersModules: List<SerializersModule>? = null, noinline requestFilter: (suspend RequestInit.() -> Unit)? = null): T = when (T::class) {")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.className}(serializersModules, requestFilter) as T")
                }
                appendLine("    else -> throw IllegalArgumentException(\"Unknown service \${T::class}\")")
                appendLine("}")
                appendLine()
                appendLine("actual inline fun <reified T : Any> getService(serializersModules: List<SerializersModule>?): T = getService(serializersModules, null)")
                appendLine()
            }
            appendLine()
        }.toString()
    }

    private fun generateCommonCodeExceptions(exceptions: List<ExceptionNameDetails>): String {
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            appendLine("package dev.kilua.rpc")
            appendLine()
            if (exceptions.isNotEmpty()) {
                appendLine("import kotlinx.serialization.json.Json")
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine("import kotlinx.serialization.modules.polymorphic")
                appendLine("import kotlinx.serialization.modules.subclass")
                appendLine()
                appendLine("private var registered = false")
                appendLine()
                appendLine("fun registerRpcServiceExceptions() {")
                appendLine("    if (!registered) {")
                appendLine("        RpcSerialization.exceptionsSerializersModule =  SerializersModule {")
                appendLine("            polymorphic(AbstractServiceException::class) {")
                exceptions.forEach {
                    appendLine("                subclass(${it.packageName}.${it.className}::class)")
                }
                appendLine("            }")
                appendLine("        }")
                appendLine("        registered = true")
                appendLine("    }")
                appendLine("}")
            } else {
                appendLine("fun registerRpcServiceExceptions() {}")
            }
            appendLine()
        }.toString()
    }

    private fun generateJvmCodeFunctions(): String {
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            appendLine("package dev.kilua.rpc")
            appendLine("import kotlinx.serialization.modules.SerializersModule")
            appendLine()
            appendLine("public actual inline fun <reified T : Any> getService(serializersModules: List<SerializersModule>?): T {")
            appendLine("    throw NotImplementedError(\"This function should be used in JS or WasmJS code only\")")
            appendLine("}")
            appendLine()
        }.toString()
    }

    private fun getTypeString(type: KSType?): String {
        return if (type == null) {
            "Unit"
        } else {
            val baseType = if (type.arguments.isEmpty()) {
                type.declaration.simpleName.asString()
            } else {
                type.declaration.simpleName.asString() + type.arguments.joinToString(",", "<", ">") {
                    it.type?.let { getTypeString(it.resolve()) } ?: it.toString()
                }
            }
            if (type.isMarkedNullable) "$baseType?" else baseType
        }
    }

    private fun getParameterList(params: List<KSValueParameter>): String {
        return if (params.isEmpty()) {
            ""
        } else {
            params.filter { it.name != null }.joinToString(", ") {
                "${it.name!!.asString()}: ${getTypeString(it.type.resolve())}"
            }
        }
    }

    private fun getParameterNames(params: List<KSValueParameter>): String {
        return params.filter { it.name != null }.joinToString(", ") {
            it.name!!.asString()
        }
    }

    private fun getTypes(type: KSType): Set<String> {
        return if (type.arguments.isNotEmpty() && type.declaration.qualifiedName != null) {
            (type.arguments.flatMap {
                if (it.type != null) {
                    getTypes(it.type!!.resolve())
                } else {
                    emptySet()
                }
            } + type.declaration.qualifiedName!!.asString()).toSet()
        } else if (type.declaration.qualifiedName != null) {
            setOf(type.declaration.qualifiedName!!.asString())
        } else {
            emptySet()
        }
    }

    private fun getTypes(methods: Sequence<KSFunctionDeclaration>): Set<String> {
        return methods.flatMap { m ->
            m.parameters.flatMap { p ->
                getTypes(p.type.resolve())
            }.toSet() + (m.returnType?.let { getTypes(it.resolve()) } ?: setOf())
        }.filterNot {
            it.startsWith("kotlin.collections.") || it.startsWith("kotlin.")
        }.toSet()
    }
}
