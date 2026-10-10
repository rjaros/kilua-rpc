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
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import dev.kilua.rpc.annotations.*
import dev.kilua.rpc.KspLayout

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
    private val logger: KSPLogger,
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
            .filterIsInstance<KSClassDeclaration>().filter { it.validate(enableNewFeatures = true) }
            .filter { it.classKind == ClassKind.INTERFACE }
            .mapNotNull { classDeclaration ->
                val interfaceName = classDeclaration.simpleName.asString()
                val packageName = classDeclaration.packageName.asString()
                val className = "${interfaceName}Impl"
                val managerName = "${interfaceName}Manager"
                val isExported = classDeclaration.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.js.JsExport"
                }
                val dependencies = classDeclaration.containingFile?.let { Dependencies(true, it) } ?: Dependencies(true)
                codeGenerator.createNewFile(dependencies, packageName, className).writer().use {
                    when (val sourceSet = sourceSetBelowKsp()) {
                        "commonMain" -> {
                            it.write(
                                generateCommonCode(
                                    packageName,
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
                                    "web.http.Request",
                                    isExported,
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
                                    "web.http.Request",
                                    isExported,
                                    classDeclaration
                                )
                            )
                        }

                        "jvmMain" -> {
                            it.write(
                                generateFrontendCode(
                                    packageName,
                                    className,
                                    interfaceName,
                                    managerName,
                                    "io.ktor.client.request.HttpRequestBuilder",
                                    false,
                                    classDeclaration,
                                    "${className}Client"
                                )
                            )
                        }

                        "androidMain" -> {
                            it.write(
                                generateFrontendCode(
                                    packageName,
                                    className,
                                    interfaceName,
                                    managerName,
                                    "io.ktor.client.request.HttpRequestBuilder",
                                    false,
                                    classDeclaration,
                                    "${className}Client"
                                )
                            )
                        }

                        else -> logger.warn(
                            "Kilua RPC: source set \"$sourceSet\" has no client flavour, so " +
                                "$className was generated empty."
                        )
                    }
                }
                services.add(NameDetails(packageName, className, interfaceName, managerName))
                classDeclaration.containingFile
            }.toList().toTypedArray()
        codeGenerator.createNewFile(Dependencies(true, *deps), "dev.kilua.rpc", "GeneratedRpcServiceManager")
            .writer().use {
                when (val sourceSet = sourceSetBelowKsp()) {
                    "commonMain" -> {
                        it.write(generateCommonCodeFunctions(services))
                    }

                    "jsMain" -> {
                        it.write(
                            generateFrontendCodeFunctions(
                                services,
                                "web.http.Request"
                            )
                        )
                    }

                    "wasmJsMain" -> {
                        it.write(
                            generateFrontendCodeFunctions(
                                services,
                                "web.http.Request"
                            )
                        )
                    }

                    "jvmMain" -> {
                        it.write(
                            generateFrontendCodeFunctions(
                                services,
                                "io.ktor.client.request.HttpRequestBuilder",
                                "GeneratedRpcServiceManagerFunctions"
                            )
                        )
                    }

                    // The android client is the same Ktor CIO client as the JVM one, so it gets the
                    // same frontend code. The only difference is where KSP writes it.
                    "androidMain" -> {
                        it.write(
                            generateFrontendCodeFunctions(
                                services,
                                "io.ktor.client.request.HttpRequestBuilder",
                                "GeneratedRpcServiceManagerFunctions"
                            )
                        )
                    }

                    else -> logger.warn(
                        "Kilua RPC: source set \"$sourceSet\" has no service manager functions " +
                            "flavour, so GeneratedRpcServiceManager was generated empty."
                    )
                }
            }
        val exceptions = mutableListOf<ExceptionNameDetails>()
        val depsExceptions = resolver.getSymbolsWithAnnotation(RpcServiceException::class.qualifiedName.orEmpty())
            .filterIsInstance<KSClassDeclaration>().filter { it.validate(enableNewFeatures = true) }
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
            when (sourceSetBelowKsp()) {
                "commonMain" -> {
                    it.write(generateCommonCodeExceptions(exceptions))
                }
            }
        }
        return emptyList()
    }

    /**
     * The source set the file the processor is currently writing into belongs to.
     *
     * Deciding this by parsing the path KSP already reported is what makes a single run able to emit
     * the common half and the platform half into different directories. [KspLayout.sourceSetOf]
     * returns null when the path belongs to no source set the processor knows about, which is warned
     * about rather than treated as an empty result: a silently empty file is far harder to notice
     * than a build log that says nothing was produced.
     */
    private fun sourceSetBelowKsp(): String? {
        val sourceSet = KspLayout.sourceSetOf(codeGenerator.generatedFile.first().toString())
        if (sourceSet == null) {
            logger.warn(
                "Kilua RPC: the file just generated is not in a source set the processor knows about, " +
                    "so it was left empty. Generated ${codeGenerator.generatedFile.first()}"
            )
        }
        return sourceSet
    }

    private fun generateCommonCode(
        packageName: String,
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
            appendImports(types)
            appendLine()
            appendLine("object $managerName : RpcServiceManager<$interfaceName>($interfaceName::class) {")
            appendLine("    init {")
            appendLine("        registerRpcServiceExceptions()")
            // workaround KSP2 issue with annotations default parameter values
            val namedRoutes = try {
                ksClassDeclaration.getAnnotationsByType(RpcService::class).firstOrNull()?.namedRoutes ?: false
            } catch (_: Exception) {
                false
            }
            // Described up front and only then emitted, so that how a method is treated cannot depend
            // on where it sits in the declaration order.
            val functions = ksClassDeclaration.getDeclaredFunctions()
                .map { collectFunctionInfo(it, interfaceName, namedRoutes) }
                .toList()
            // A streaming method is declared twice by convention: once as the raw channel function
            // and once as the handler that receives them the other way round. The channel branch
            // below emits the bind for the raw function, which already covers the handler, so a
            // plain bind must not be emitted for the handler's own declaration as well.
            val channelNames = functions.filter { it.kind.isChannel }.map { it.name }.toSet()
            functions.forEach { info ->
                val name = info.name
                val route = info.route?.let { "\"$it\"" }
                when (info.kind) {
                    RpcMethodKind.REMOTE_DATA ->
                        appendLine("        bindRemoteData($interfaceName::$name, $route)")

                    RpcMethodKind.WEBSOCKET, RpcMethodKind.SSE ->
                        // The cast keeps overload resolution on the two argument bind rather than the
                        // three argument one.
                        appendLine("        bind($interfaceName::$name, ${route ?: "null as String?"})")

                    RpcMethodKind.CALL ->
                        if (name !in channelNames)
                            appendLine("        bind($interfaceName::$name, ${info.httpMethod}, $route)")
                }
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
        requestClass: String,
        isExported: Boolean,
        ksClassDeclaration: KSClassDeclaration,
        fileJvmName: String? = null
    ): String {
        val requestClassName = requestClass.substringAfterLast('.')
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            fileJvmName?.let { appendLine("@file:JvmName(\"$it\")") }
            appendLine("package $packageName")
            appendLine()
            appendLine("import $requestClass")
            appendLine("import dev.kilua.rpc.RpcAgent")
            if (isExported) {
                appendLine("import kotlin.js.ExperimentalJsExport")
                appendLine("import kotlin.js.JsExport")
            }
            appendLine("import kotlinx.serialization.modules.SerializersModule")
            val types = getTypes(ksClassDeclaration.getDeclaredFunctions())
            appendImports(types)
            appendLine()
            if (isExported) {
                appendLine("@OptIn(ExperimentalJsExport::class)")
                appendLine("@JsExport")
            }
            appendLine("class $className(baseUrl: String? = null, serializersModules: List<SerializersModule>? = null, requestFilter: (suspend $requestClassName.() -> Unit)? = null) : $interfaceName, RpcAgent<$interfaceName>(baseUrl, $managerName, serializersModules, requestFilter) {")
            // workaround KSP2 issue with annotations default parameter values
            val namedRoutes = try {
                ksClassDeclaration.getAnnotationsByType(RpcService::class).firstOrNull()?.namedRoutes ?: false
            } catch (_: Exception) {
                false
            }
            // Described up front and only then emitted, so that how a method is overridden cannot
            // depend on where it sits in the declaration order.
            val functions = ksClassDeclaration.getDeclaredFunctions()
                .map { collectFunctionInfo(it, interfaceName, namedRoutes) }
                .toList()
            // A streaming method is declared twice by convention: once as the raw channel function and
            // once as the handler that receives them the other way round. The channel branch below
            // emits an override for the handler as well, so the handler's own declaration must not
            // turn into an additional `call` override on top of it.
            val channelNames = functions.filter { it.kind.isChannel }.map { it.name }.toSet()
            functions.forEach { info ->
                val name = info.name
                val params = info.parameters
                when (info.kind) {
                    RpcMethodKind.CALL ->
                        if (name !in channelNames) {
                            if (params.isEmpty()) {
                                appendLine("    override suspend fun $name() = call($interfaceName::$name)")
                            } else {
                                appendLine(
                                    "    override suspend fun $name(${getParameterList(params)}) = " +
                                        "call($interfaceName::$name, ${getParameterNames(params)})"
                                )
                            }
                        }

                    RpcMethodKind.REMOTE_DATA ->
                        if (params.isEmpty()) {
                            appendLine("    override suspend fun $name() = call($interfaceName::$name)")
                        } else {
                            appendLine(
                                "    override suspend fun $name(${getParameterList(params)}) = " +
                                    "${getTypeString(info.returnType)}()"
                            )
                        }

                    RpcMethodKind.WEBSOCKET -> {
                        appendLine("    override suspend fun $name(${getParameterList(params)}) {}")
                        // The handler runs with the channels the other way round from the declaration.
                        val outgoing = flipChannelDirection(params[0].type.resolve())
                        val incoming = flipChannelDirection(params[1].type.resolve())
                        appendLine(
                            "    override suspend fun $name(handler: suspend ($outgoing, $incoming) -> Unit) = " +
                                "webSocket($interfaceName::$name, handler)"
                        )
                    }

                    RpcMethodKind.SSE -> {
                        appendLine("    override suspend fun $name(${getParameterList(params)}) {}")
                        val incoming = flipChannelDirection(params[0].type.resolve())
                        appendLine(
                            "    override suspend fun $name(handler: suspend ($incoming) -> Unit) = " +
                                "sseConnection($interfaceName::$name, handler)"
                        )
                    }
                }
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
                appendLine("    baseUrl: String? = null,")
                appendLine("    serializersModules: List<SerializersModule>? = null")
                appendLine("): T")
            }
            appendLine()
        }.toString()
    }

    private fun generateFrontendCodeFunctions(
        services: List<NameDetails>,
        requestClass: String,
        fileJvmName: String? = null
    ): String {
        val requestClassName = requestClass.substringAfterLast('.')
        return StringBuilder().apply {
            appendLine("//")
            appendLine("// GENERATED by Kilua RPC")
            appendLine("//")
            if (services.isNotEmpty()) {
                fileJvmName?.let { appendLine("@file:JvmName(\"$it\")") }
                appendLine("package dev.kilua.rpc")
                appendLine()
                appendLine("import $requestClass")
                appendLine("import kotlinx.serialization.modules.SerializersModule")
                appendLine()
                appendLine("inline fun <reified T : Any> getService(baseUrl: String? = null, serializersModules: List<SerializersModule>? = null, noinline requestFilter: (suspend $requestClassName.() -> Unit)? = null): T = when (T::class) {")
                services.forEach {
                    appendLine("    ${it.packageName}.${it.interfaceName}::class -> ${it.packageName}.${it.className}(baseUrl, serializersModules, requestFilter) as T")
                }
                appendLine("    else -> throw IllegalArgumentException(\"Unknown service \${T::class}\")")
                appendLine("}")
                appendLine()
                appendLine("actual inline fun <reified T : Any> getService(baseUrl: String?, serializersModules: List<SerializersModule>?): T = getService(baseUrl, serializersModules, null)")
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

    /**
     * How a remote method is transported. It is derived from the resolved types of the declaration
     * rather than from its rendered text, so a user type that merely shares a name with one of the
     * recognised ones is never mistaken for it.
     */
    private enum class RpcMethodKind {
        /** A plain request/response call. */
        CALL,

        /** A call returning a paginated `RemoteData` slab. */
        REMOTE_DATA,

        /** A two way call over WebSockets. */
        WEBSOCKET,

        /** A one way stream of server sent events. */
        SSE;

        /**
         * True for the kinds emitted through a channel branch, which covers both the raw declaration
         * and the handler overload the convention asks for.
         */
        val isChannel: Boolean get() = this == WEBSOCKET || this == SSE
    }

    /**
     * Everything the two emitters need about a single declared method, gathered in one pass so that
     * the decision about a whole interface is made before anything is written out.
     */
    private data class FunctionInfo(
        val name: String,
        val kind: RpcMethodKind,
        val parameters: List<KSValueParameter>,
        val returnType: KSType?,
        val httpMethod: String,
        val route: String?
    )

    private fun collectFunctionInfo(
        function: KSFunctionDeclaration,
        interfaceName: String,
        namedRoutes: Boolean
    ): FunctionInfo {
        val params = function.parameters
        val kind = when {
            function.returnType?.resolve()?.isTypeOf(REMOTE_DATA) == true -> RpcMethodKind.REMOTE_DATA
            params.size == 2 && params[0].type.resolve().isTypeOf(RECEIVE_CHANNEL) -> RpcMethodKind.WEBSOCKET
            params.size == 1 && params[0].type.resolve().isTypeOf(SEND_CHANNEL) -> RpcMethodKind.SSE
            else -> RpcMethodKind.CALL
        }
        val (method, route) = httpMethodAndRoute(function, interfaceName, namedRoutes)
        return FunctionInfo(
            name = function.simpleName.asString(),
            kind = kind,
            parameters = params,
            returnType = function.returnType?.resolve(),
            httpMethod = method,
            route = route
        )
    }

    /**
     * The HTTP method and route a method is served by, taking explicit bindings into account and
     * falling back to a generated route when [namedRoutes] is on.
     */
    private fun httpMethodAndRoute(
        function: KSFunctionDeclaration,
        interfaceName: String,
        namedRoutes: Boolean
    ): Pair<String, String?> {
        val autoRoute = if (namedRoutes) "$interfaceName.${function.simpleName.asString()}" else null
        val rpcBinding = function.getAnnotationsByType(RpcBinding::class).firstOrNull()
        if (rpcBinding != null) return "HttpMethod.${rpcBinding.method.name}" to rpcBinding.route
        val rpcBindingMethod = function.getAnnotationsByType(RpcBindingMethod::class).firstOrNull()
        if (rpcBindingMethod != null) return "HttpMethod.${rpcBindingMethod.method.name}" to autoRoute
        val rpcBindingRoute = function.getAnnotationsByType(RpcBindingRoute::class).firstOrNull()
        if (rpcBindingRoute != null) return "HttpMethod.POST" to rpcBindingRoute.route
        return "HttpMethod.POST" to autoRoute
    }

    /**
     * The qualified name of the declaration this type refers to, or null when it does not have one,
     * as is the case for type parameters.
     */
    private val KSType.qualifiedName: String?
        get() = declaration.qualifiedName?.asString()

    /** True when this type is exactly the library type [qualifiedName], ignoring type arguments. */
    private fun KSType.isTypeOf(qualifiedName: String): Boolean = this.qualifiedName == qualifiedName

    /**
     * Render [type] with its channel direction flipped.
     *
     * The declaration is resolved rather than rewritten as text: the element type comes from the
     * actual type arguments, so a user type whose name merely contains "SendChannel" cannot be
     * mangled by a blind string replacement.
     */
    private fun flipChannelDirection(type: KSType): String {
        val other = when (type.qualifiedName) {
            SEND_CHANNEL -> RECEIVE_CHANNEL
            RECEIVE_CHANNEL -> SEND_CHANNEL
            else -> error("Cannot flip the channel direction of ${type.qualifiedName ?: type}")
        }
        val element = type.arguments.singleOrNull()?.type?.let { getTypeString(it.resolve()) }
        val suffix = if (type.isMarkedNullable) "?" else ""
        val simpleName = other.substringAfterLast('.')
        return if (element == null) "$simpleName$suffix" else "$simpleName<$element>$suffix"
    }

    /**
     * Emit the imports a generated file needs for every type reachable from [types].
     *
     * A channel method names only one channel in its parameter list, but its client override flips
     * the direction and so names the other one too. Without this, an interface whose only streaming
     * method is SSE would generate an override referring to an unimported ReceiveChannel.
     */
    private fun StringBuilder.appendImports(types: Set<String>) {
        val imports = if (SEND_CHANNEL in types || RECEIVE_CHANNEL in types) {
            types + SEND_CHANNEL + RECEIVE_CHANNEL
        } else {
            types
        }
        imports.sorted().forEach { appendLine("import $it") }
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
        }.toSet()
    }

    private companion object {
        /** `RemoteData<T>` is recognised by its declaration, never by the name alone. */
        const val REMOTE_DATA = "dev.kilua.rpc.RemoteData"
        const val RECEIVE_CHANNEL = "kotlinx.coroutines.channels.ReceiveChannel"
        const val SEND_CHANNEL = "kotlinx.coroutines.channels.SendChannel"
    }
}
