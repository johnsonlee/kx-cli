package io.johnsonlee.exec.cmd

import java.io.File
import kotlinx.coroutines.flow.collect
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

    override fun run() = runBlocking {
        File(output).printWriter().use { printer ->
            if (noHeader) {
                // ignore header
            } else if (::header.isInitialized && header.isNotEmpty()) {
                printer.println(header.joinToString(delimiter))
            }

            fetch { input ->
                input to parse(input)
            }.map { (input, root) ->
                val global = Variable(root)
                val source = Variable(root.newTextNode(input).context)
                val variables = VariableTable(global, mapOf("\$" to global, "\$__source__" to source))
                val initialPipeline = listOf(PipemillContext(rowExpression, variables))
                val pipemills = parsePipemills(rowExpression)

                evaluatePipeline(initialPipeline, pipemills).forEach { row ->
                    printer.println(row.joinToString(delimiter) {
                        it.text.takeIf(String::isNotBlank)?.let { text ->
                            "$textQualifier${text.trim()}$textQualifier"
                        } ?: ""
                    })
                }
            }.collect()
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

    private fun evaluatePipeline(currentNodes: List<PipemillContext>, steps: List<Pipemill>, stepIndex: Int = 0): Sequence<List<DOMNode>> {
        if (stepIndex >= steps.size || steps.size == 1) {
            return currentNodes.map { pipeline ->
                evaluatePipemill(steps.last(), pipeline.variables)
            }.asSequence().flatten()
        }

        val step = steps[stepIndex]
        val nextNodes = currentNodes.flatMap { pipeline ->
            parseDownstreamPipemillContext(pipeline, step)
        }

        return evaluatePipeline(nextNodes, steps, stepIndex + 1)
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

    private fun evaluatePipemill(pipemill: Pipemill, variables: VariableTable): Sequence<List<DOMNode>> = when (pipemill) {
        is SimplePipemill -> expandSimplePipemill(pipemill, variables)
        is ObjectPipemill -> expandObjectPipemill(pipemill, variables)
    }

    private fun expandObjectPipemill(pipemill: ObjectPipemill, variables: VariableTable): Sequence<List<DOMNode>> = sequence {
        val extractedValues = pipemill.objectDefinition.mapValues { (_, path) ->
            variables.evaluate(path)
        }

        val rows = if (extractedValues.values.all { it.size <= 1 }) {
            listOf(extractedValues.values.map(List<DOMNode>::first))
        } else {
            val staticValues = extractedValues.filterValues {
                it.size <= 1
            }.mapValues {
                it.value.first()
            }
            val arrayValues = extractedValues.filterValues { it.size > 1 }

            cartesianProduct(arrayValues).map {
                staticValues + it
            }.map {
                it.values.toList()
            }
        }

        rows.forEach {
            yield(it)
        }
    }

    private fun expandSimplePipemill(pipemill: SimplePipemill, variables: VariableTable) = sequence {
        val extractedValues = variables.evaluate(pipemill.path)
        extractedValues.forEach {
            yield(listOf(it))
        }
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
