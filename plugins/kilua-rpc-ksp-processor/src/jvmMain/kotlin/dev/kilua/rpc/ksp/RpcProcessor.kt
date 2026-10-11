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
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import dev.kilua.rpc.annotations.*
import java.io.Writer

public data class NameDetails(
    val packageName: String,
    val className: String,
    val interfaceName: String,
    val managerName: String
)

public data class ExceptionNameDetails(val packageName: String, val className: String)

/**
 * Reads annotated service interfaces and writes out the two halves of generated code for each.
 *
 * This is the half that knows about KSP, and it does nothing else: it resolves symbols, reduces them
 * to [InterfaceInfo] values, and hands those to [RpcCodeRenderer], which is where the text is
 * produced. Everything awkward about the generated code therefore lives in a place that can be
 * tested without a resolver.
 */
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
        // Collected before anything is generated, and derived from the declarations rather than
        // produced as a side effect of visiting them: reading the containing file to filter on is a
        // separate concern from registering a service, and mixing the two meant a declaration with no
        // containing file - one from a binary dependency, say - dropped out of the dependencies of a
        // file that still lists it.
        val serviceDeclarations = resolver.getSymbolsWithAnnotation(RpcService::class.qualifiedName.orEmpty())
            .filterIsInstance<KSClassDeclaration>().filter { it.validate(enableNewFeatures = true) }
            .filter { it.classKind == ClassKind.INTERFACE }
            .toList()
        val deps = serviceDeclarations.mapNotNull { it.containingFile }.toTypedArray()
        serviceDeclarations.forEach { classDeclaration ->
            val interfaceName = classDeclaration.simpleName.asString()
            val packageName = classDeclaration.packageName.asString()
            val className = "${interfaceName}Impl"
            val managerName = "${interfaceName}Manager"
            // Whether the interface asks to be exported to JavaScript. Only the web flavours act on
            // it: the annotation is a Kotlin/JS one, and emitting it anywhere else is a compile error.
            val isExported = classDeclaration.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.js.JsExport"
            }
            val dependencies = classDeclaration.containingFile?.let { Dependencies(true, it) } ?: Dependencies(true)
            services.add(NameDetails(packageName, className, interfaceName, managerName))
            createNewFile(dependencies, packageName, className) { writer, sourceSet ->
                val info = interfaceInfo(classDeclaration, interfaceName, className, managerName)
                when (sourceSet) {
                    "commonMain" -> writer.write(RpcCodeRenderer.renderManager(info))

                    "jsMain", "wasmJsMain" -> writer.write(
                        RpcCodeRenderer.renderClient(info, "web.http.Request", null, isExported)
                    )

                    "jvmMain", "androidMain" -> writer.write(
                        RpcCodeRenderer.renderClient(
                            info,
                            "io.ktor.client.request.HttpRequestBuilder",
                            "${className}Client",
                            isExported = false
                        )
                    )

                    else -> logger.warn(
                        "Kilua RPC: source set \"$sourceSet\" has no client flavour, so " +
                            "$className was generated empty."
                    )
                }
            }
        }
        createNewFile(Dependencies(true, *deps), "dev.kilua.rpc", "GeneratedRpcServiceManager") { writer, sourceSet ->
            when (sourceSet) {
                "commonMain" -> writer.write(RpcCodeRenderer.renderManagerFunctions(services))

                "jsMain", "wasmJsMain" -> writer.write(
                    RpcCodeRenderer.renderClientFunctions(services, "web.http.Request", null)
                )

                "jvmMain", "androidMain" -> writer.write(
                    RpcCodeRenderer.renderClientFunctions(
                        services,
                        "io.ktor.client.request.HttpRequestBuilder",
                        "GeneratedRpcServiceManagerFunctions"
                    )
                )

                else -> logger.warn(
                    "Kilua RPC: source set \"$sourceSet\" has no service manager functions flavour, " +
                        "so GeneratedRpcServiceManager was generated empty."
                )
            }
        }
        // Same shape as the services above: register every declaration, and derive the dependencies
        // from them, instead of doing one as a side effect of filtering for the other.
        val exceptions = mutableListOf<ExceptionNameDetails>()
        val exceptionDeclarations =
            resolver.getSymbolsWithAnnotation(RpcServiceException::class.qualifiedName.orEmpty())
                .filterIsInstance<KSClassDeclaration>().filter { it.validate(enableNewFeatures = true) }
                .filter { it.classKind == ClassKind.CLASS }
                .toList()
        val depsExceptions = exceptionDeclarations.mapNotNull { it.containingFile }.toTypedArray()
        exceptionDeclarations.forEach { classDeclaration ->
            exceptions.add(
                ExceptionNameDetails(
                    classDeclaration.packageName.asString(),
                    classDeclaration.simpleName.asString()
                )
            )
        }
        createNewFile(Dependencies(true, *depsExceptions), "dev.kilua.rpc", "GeneratedRpcServiceExceptions") { writer, sourceSet ->
            when (sourceSet) {
                "commonMain" -> writer.write(RpcCodeRenderer.renderExceptions(exceptions))

                else -> {} // Nothing to do: exceptions are only used in commonMain.
            }
        }
        return emptyList()
    }

    /**
     * Create [fileName] in [packageName] and hand both the writer and the source set the file landed
     * in to [block].
     *
     * The source set has to come from a file KSP actually created, because that is the only thing
     * which says where the output went. It is found by diffing what KSP reports before and after the
     * call rather than by reading a position in that collection: the collection is a snapshot of
     * everything created in this round, so with several services the just created file is not its
     * first entry, and taking the first entry of an empty collection would throw. This costs a
     * re-materialisation per file, which is immaterial at realistic service counts and deliberately
     * cheaper than carrying a running set.
     */
    private fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        block: (Writer, String?) -> Unit
    ) {
        val known = codeGenerator.generatedFile.toSet()
        val writer = codeGenerator.createNewFile(dependencies, packageName, fileName).writer()
        val created = codeGenerator.generatedFile.firstOrNull { it !in known }
        writer.use { block(it, created?.path?.let(KspLayout::sourceSetOf)) }
    }

    /** Reduce an annotated interface to the plain data the renderer works from. */
    private fun interfaceInfo(
        classDeclaration: KSClassDeclaration,
        interfaceName: String,
        className: String,
        managerName: String
    ): InterfaceInfo {
        val namedRoutes = classDeclaration.namedRoutes()
        val functions = classDeclaration.getDeclaredFunctions().map {
            functionInfo(it, interfaceName, namedRoutes)
        }.toList()
        return InterfaceInfo(
            packageName = classDeclaration.packageName.asString(),
            interfaceName = interfaceName,
            className = className,
            managerName = managerName,
            functions = functions,
            types = getTypes(classDeclaration.getDeclaredFunctions())
        )
    }

    private fun functionInfo(
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
        val [method, route] = httpMethodAndRoute(function, interfaceName, namedRoutes)
        return FunctionInfo(
            name = function.simpleName.asString(),
            kind = kind,
            parameters = params.mapNotNull { parameter ->
                parameter.name?.asString()?.let { RenderParameter(it, getTypeString(parameter.type.resolve())) }
            },
            returnType = getTypeString(function.returnType?.resolve()),
            httpMethod = method,
            route = route,
            flippedParameters = when (kind) {
                RpcMethodKind.WEBSOCKET ->
                    listOf(
                        flipChannelDirection(params[0].type.resolve()),
                        flipChannelDirection(params[1].type.resolve())
                    )

                RpcMethodKind.SSE -> listOf(flipChannelDirection(params[0].type.resolve()))
                RpcMethodKind.CALL, RpcMethodKind.REMOTE_DATA -> emptyList()
            }
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
     * The fully qualified names of every type the client needs imported, walked from the declared
     * parameters only.
     *
     * Return types are deliberately not collected: the generated client never writes a return type
     * (overrides infer it from the interface), so importing them would only produce unused imports.
     */
    private fun getTypes(methods: Sequence<KSFunctionDeclaration>): Set<String> {
        return methods.flatMap { m ->
            m.parameters.flatMap { p ->
                getTypes(p.type.resolve())
            }.toSet()
        }.toSet()
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

    /** The `qualified name` reported for the declaration a type refers to, when it has one. */
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

    /**
     * Whether the service is bound with generated route names.
     *
     * Wrapped in a try because of a KSP2 issue reading default parameter values off an annotation;
     * without it, a service that simply does not set `namedRoutes` would fail instead of getting
     * false.
     */
    private fun KSClassDeclaration.namedRoutes(): Boolean = try {
        getAnnotationsByType(RpcService::class).firstOrNull()?.namedRoutes ?: false
    } catch (_: Exception) {
        false
    }

    private companion object {
        /** `RemoteData<T>` is recognised by its declaration, never by the name alone. */
        const val REMOTE_DATA = "dev.kilua.rpc.RemoteData"
        const val RECEIVE_CHANNEL = "kotlinx.coroutines.channels.ReceiveChannel"
        const val SEND_CHANNEL = "kotlinx.coroutines.channels.SendChannel"
    }
}
