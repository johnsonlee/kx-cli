package io.johnsonlee.kx.cmd

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.Option

abstract class DOM2CSVCommand : FetchCommand(), DOMParser {

    @Option(names = ["-h", "--header"], description = ["Header of columns"], required = false)
    lateinit var header: List<String>

    @Option(names = ["--no-header"], description = ["No header row"], required = false)
    var noHeader: Boolean = false

    @Option(names = ["-r", "--row"], description = ["Row expression"], required = true)
    lateinit var rowExpression: String

    @Option(names = ["-d", "--delimiter"], description = ["Delimiter of columns"], required = false, defaultValue = ",")
    lateinit var delimiter: String

    @Option(names = ["-q", "--text-qualifier"], description = ["Text qualifier of columns"], required = false, defaultValue = "\"")
    lateinit var textQualifier: String

    override fun parse(input: String): DOMContext = get(input) {
        parse(it, input)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun run() = runBlocking {
        File(output).printWriter().use { printer ->
            if (noHeader) {
                // ignore header
            } else if (::header.isInitialized && header.isNotEmpty()) {
                printer.println(header.joinToString(delimiter))
            }

            input.asFlow().flatMapMerge { url ->
                flow {
                    val root = parse(url)
                    val global = Variable(root)
                    val source = Variable(root.newTextNode(url).context)
                    val variables = VariableTable(global, mapOf("\$" to global, "\$__source__" to source))
                    val initialPipeline = listOf(PipemillContext(rowExpression, variables))
                    val pipemills = parsePipemills(rowExpression)
                    emitAll(evaluatePipeline(initialPipeline, pipemills))
                }
            }.flowOn(Dispatchers.IO).collect { row ->
                printer.println(row.joinToString(delimiter) {
                    it.text.takeIf(String::isNotBlank)?.let { text ->
                        "$textQualifier${text.trim()}$textQualifier"
                    } ?: ""
                })
                printer.flush()
            }
        }
    }

    private fun parsePipemills(expression: String): List<Pipemill> = expression.split("|").map {
        parsePipemill(it.trim())
    }

    private fun parsePipemill(pipemill: String, raw: String = pipemill): Pipemill = when {
        // scenario:
        // - .foo[*] as $foo
        // - {foo: .foo, bar: .bar} as $baz
        "as $" in pipemill -> {
            val (path, alias) = pipemill.split("as", limit = 2).map(String::trim)
            parsePipemill(path, pipemill).copy(alias = alias)
        }
        // scenario:
        // - {foo: .foo, bar: .bar}"
        pipemill.matches(regexObject) -> ObjectPipemill(pipemill, raw)
        // scenario:
        // - .foo[*].bar
        else -> SimplePipemill(pipemill, raw)
    }

    private suspend fun evaluatePipeline(currentNodes: List<PipemillContext>, steps: List<Pipemill>) = flow {
        var current = currentNodes

        for ((index, step) in steps.withIndex()) {
            val isLast = index == steps.lastIndex
            val next = mutableListOf<PipemillContext>()

            for (pipeline in current) {
                if (isLast) {
                    emitAll(evaluatePipemill(step, pipeline.variables))
                } else {
                    val results = parseDownstreamPipemillContext(pipeline, step)
                    next += results
                }
            }

            current = next
            if (current.isEmpty()) break
        }
    }

    private fun parseDownstreamPipemillContext(
        pipeline: PipemillContext,
        step: Pipemill
    ): List<PipemillContext> {
        return when (step) {
            is SimplePipemill -> parseSimplePipemillContext(pipeline, step)
            is ObjectPipemill -> parseObjectPipemillContext(pipeline, step)
        }
    }

    private fun parseSimplePipemillContext(
        pipeline: PipemillContext,
        step: SimplePipemill
    ): List<PipemillContext> {
        val nodes = pipeline.variables.evaluate(step.path)
        return nodes.map { node ->
            val newVariables = pipeline.variables + (step.alias to Variable(node.context))
            PipemillContext(step.path, newVariables)
        }
    }

    private fun parseObjectPipemillContext(
        pipeline: PipemillContext,
        step: ObjectPipemill
    ): List<PipemillContext> {
        val nodes = step.objectDefinition.mapValues { (_, path) ->
            pipeline.variables.evaluate(path)
        }

        val rows = if (nodes.values.all { it.size <= 1 }) {
            val attrs = nodes.mapValues { it.value.first() }.toMap()
            listOf(pipeline.variables.root.context.newNode(attrs))
        } else {
            val staticValues = nodes.filterValues { it.size <= 1 }
                .mapValues { it.value.first() }
            val arrayValues = nodes.filterValues { it.size > 1 }

            cartesianProduct(arrayValues).map {
                staticValues + it
            }.map {
               pipeline.variables.root.context.newNode(it)
            }
        }

        return rows.map { row ->
            val newVariables = pipeline.variables + (step.alias to Variable(row.context))
            PipemillContext(step.expression, newVariables)
        }
    }

    private fun evaluatePipemill(pipemill: Pipemill, variables: VariableTable) = when (pipemill) {
        is SimplePipemill -> expandSimplePipemill(pipemill, variables)
        is ObjectPipemill -> expandObjectPipemill(pipemill, variables)
    }

    private fun expandObjectPipemill(pipemill: ObjectPipemill, variables: VariableTable): Flow<List<DOMNode>> {
        val extractedValues = pipemill.objectDefinition.mapValues { (_, path) ->
            variables.evaluate(path)
        }

        if (extractedValues.values.all { it.size <= 1 }) {
            return flowOf(extractedValues.values.map(List<DOMNode>::first))
        }

        val staticValues = extractedValues.filterValues {
            it.size <= 1
        }.mapValues {
            it.value.first()
        }
        val arrayValues = extractedValues.filterValues { it.size > 1 }

        return cartesianProduct(arrayValues).asFlow().map {
            (staticValues + it).values.toList()
        }
    }

    private fun expandSimplePipemill(pipemill: SimplePipemill, variables: VariableTable): Flow<List<DOMNode>> {
        val extractedValues = variables.evaluate(pipemill.path)
        return extractedValues.asFlow().map(::listOf)
    }

    private fun cartesianProduct(columns: Map<String, List<DOMNode>>): List<Map<String, DOMNode>> {
        if (columns.isEmpty()) return listOf(emptyMap())

        val keys = columns.keys.toList()
        val result = mutableListOf<Map<String, DOMNode>>()

        fun buildRow(index: Int, currentRow: Map<String, DOMNode>) {
            if (index == keys.size) {
                result.add(currentRow.toMap())
                return
            }

            val key = keys[index]
            val values = columns[key] ?: emptyList()

            for (value in values) {
                buildRow(index + 1, currentRow + (key to value))
            }
        }

        buildRow(0, emptyMap())
        return result
    }

}

private val regexObject = "\\s*\\{[^{}]+\\}\\s*".toRegex()

private val regexNameValuePairs = "\\s*(\\w+)\\s*:\\s*([^{},]+)\\s*".toRegex()

private data class SimplePipemill(
    val path: String,
    private val raw: String = path,
    override val alias: String = "$",
) : Pipemill {
    override fun copy(alias: String): SimplePipemill = copy(path = path, alias = alias)

    override fun toString(): String = raw
}

private data class ObjectPipemill(
    val expression: String,
    private val raw: String = expression,
    override val alias: String = "$",
) : Pipemill {

    val objectDefinition: Map<String, String> by lazy {
        regexNameValuePairs.findAll(expression).associate { it.groupValues[1] to it.groupValues[2] }
    }

    override fun copy(alias: String): Pipemill = copy(expression = expression, alias = alias)

    override fun toString(): String = raw
}
