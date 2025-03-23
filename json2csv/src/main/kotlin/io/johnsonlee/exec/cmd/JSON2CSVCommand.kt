package io.johnsonlee.exec.cmd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auto.service.AutoService
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
import io.johnsonlee.exec.cmd.JSON2CSVCommand.Companion.objectMapper
import io.johnsonlee.exec.internal.capitalize
import java.io.File
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Option

/**
 * Converts JSON into CSV with flexible row selection and powerful column extraction capabilities.
 *
 * ## Overview
 *
 * This command reads a JSON file, selects rows using a JSONPath expression,
 * and extracts columns using a flexible **column definition DSL**.
 *
 * Each row becomes a line in the CSV output, and column values are extracted from each row object
 * based on the following supported column definition syntax.
 *
 * ## Supported Column Definition Features
 *
 * ### 1. Standard JsonPath
 *
 * Each column can extract its value using **standard JsonPath expressions**, either absolute or relative.
 *
 * Example (absolute path):
 * ```
 * $.items[*].name
 * ```
 *
 * Example (relative path inside row):
 * ```
 * items[*].name
 * ```
 *
 * ### 2. Pipeline Processing (Chained Filters)
 *
 * Columns can apply **pipeline processing**, where multiple steps process the extracted data.
 * Each step receives the result of the previous step.
 *
 * Example:
 * ```
 * .items[*] | {id:.id, name:.name}
 * ```
 *
 * ### 3. Object Definition Columns
 *
 * Columns can be **composite objects**, combining multiple fields into a single JSON object.
 *
 * Example:
 * ```
 * .items[*] | {id:.id, name:.name, status:$.status}
 * ```
 *
 * ### 4. Named Global Variables
 * Named variables can be dynamically created within pipelines using the `as $variableName` syntax.
 *
 * Example:
 * ```
 * items[*] as $items
 * ```
 * This defines `$items`, which can then be referenced later:
 * ```
 * $items.name
 * ```
 * Named variables can be used to store intermediate results, making complex pipelines more readable.
 * Example:
 * ```
 * items[*] as $item | {id:$item.id, name:$item.name}
 * ```
 *
 * Named variables:
 * - Must start with `$`
 * - Can be objects, arrays, or primitive values
 * - Are scoped to the current row
 *
 * ### 5. Simple JsonPath (Relative Paths)
 *
 * Column paths can be simple relative paths if they refer to fields directly under the current row object.
 *
 * Example:
 * ```
 * order_id:id
 * ```
 * This extracts `id` from each row object directly.
 *
 * ## Array Handling
 *
 * When a column maps to an array, each element becomes a separate row.
 * When multiple columns map to arrays, a **Cartesian product** of all arrays is produced.
 *
 * ## Example Command
 *
 * ```
 * java -jar exec.jar exec json2csv \
 *     --input input.json \
 *     --output output.csv \
 *     --row '$.items[*]' \
 *     --header id,name \
 *     --delimiter ','
 * ```
 *
 * ## Options
 *
 * - `--input`: Path to the input JSON file.
 * - `--output`: Path to the output CSV file.
 * - `--row`: JSONPath expression selecting each row object.
 * - `--header`: List of column names, in order.
 * - `--delimiter`: Column delimiter (default is `,`).
 *
 * For further details and examples, see project documentation.
 */
@AutoService(Command::class)
class JSON2CSVCommand : FetchCommand() {

    companion object {
        val objectMapper = jacksonObjectMapper().apply {
            registerKotlinModule()
            registerModules(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    @Option(names = ["-h", "--header"], description = ["Header of columns"], required = false)
    lateinit var header: List<String>

    @Option(names = ["--no-header"], description = ["No header row"], required = false)
    var noHeader: Boolean = false

    @Option(names = ["-r", "--row"], description = ["Row expression"])
    lateinit var rowExpression: String

    @Option(names = ["-d", "--delimiter"], description = ["Delimiter of columns"], required = false, defaultValue = ",")
    lateinit var delimiter: String

    @Option(names = ["-q", "--text-qualifier"], description = ["Text qualifier of columns"], required = false, defaultValue = "\"")
    lateinit var textQualifier: String

    override fun run() {
        val root = loadDocument(input)
        val global = Variable(root)
        val input = Variable(TextNode.valueOf(input).toDocument())
        val variables = VariableTable(global, mapOf("\$" to global, "\$__input__" to input))

        File(output).printWriter().use { printer ->
            val initialPipeline = listOf(PipemillContext(rowExpression, variables))
            val steps = parsePipemills(rowExpression)

            if (noHeader) {
                // ignore header
            } else if (::header.isInitialized && header.isNotEmpty()) {
                printer.println(header.joinToString(delimiter))
            } else {
                (steps.last() as? ObjectPipemill)?.objectDefinition?.keys?.joinToString(delimiter) {
                    it.capitalize()
                }?.let(printer::println)
            }

            evaluatePipeline(initialPipeline, steps).forEach { row ->
                printer.println(row.joinToString(delimiter) {
                    it.asText().takeIf(String::isNotBlank)?.let { text ->
                        "$textQualifier${text}$textQualifier"
                    } ?: ""
                })
            }
        }
    }

    private fun loadDocument(input: String): Document {
        val stream = if (input.startsWith("http://") || input.startsWith("https://")) {
            val response = get(url())
            if (response.isSuccessful) {
                response.body?.byteStream() ?: error("Loading JSON from $input failed")
            } else {
                error("Loading JSON from $input failed: ${response.code} ${response.message}")
            }
        } else {
            File(input).inputStream()
        }

        val json = stream.buffered().use(objectMapper::readTree)
        return JsonPath.using(jsonPathCfg).parse(json)
    }

    private fun evaluateRowExpression(expression: String, variables: VariableTable): Sequence<List<Node>> {
        val initialPipeline = listOf(
            PipemillContext(expression, variables)
        )
        val steps = parsePipemills(expression)
        return evaluatePipeline(initialPipeline, steps, 0)
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

    private fun evaluatePipeline(currentNodes: List<PipemillContext>, steps: List<Pipemill>, stepIndex: Int = 0): Sequence<List<Node>> {
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
        val nodes = pipeline.variables.root.context.evaluate<Node>(step.path)
        return nodes.map { node ->
            val newVariables = pipeline.variables + (step.alias to Variable(node.toDocument()))
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
            listOf(nodes.mapValues { it.value.first() }.toMap().toNode())
        } else {
            val staticValues = nodes.filterValues { it.size <= 1 }
                .mapValues { it.value.first() }
            val arrayValues = nodes.filterValues { it.size > 1 }

            cartesianProduct(arrayValues).map {
                staticValues + it
            }.map {
                it.toNode()
            }
        }

        return rows.map { row ->
            val newVariables = pipeline.variables + (step.alias to Variable(row.toDocument()))
            PipemillContext(step.expression, newVariables)
        }
    }

    private fun evaluatePipemill(pipemill: Pipemill, variables: VariableTable): Sequence<List<Node>> = when (pipemill) {
        is SimplePipemill -> expandSimplePipemill(pipemill, variables)
        is ObjectPipemill -> expandObjectPipemill(pipemill, variables)
    }

    private fun expandObjectPipemill(pipemill: ObjectPipemill, variables: VariableTable): Sequence<List<Node>> = sequence {
        val extractedValues = pipemill.objectDefinition.mapValues { (_, path) ->
            variables.evaluate(path)
        }

        val rows = if (extractedValues.values.all { it.size <= 1 }) {
            listOf(extractedValues.values.map(List<Node>::first))
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

    private fun cartesianProduct(columns: Map<String, List<Node>>): List<Map<String, Node>> {
        if (columns.isEmpty()) return listOf(emptyMap())

        val keys = columns.keys.toList()
        val result = mutableListOf<Map<String, Node>>()

        fun buildRow(index: Int, currentRow: Map<String, Node>) {
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

private val jsonPathCfg = Configuration.builder()
    .jsonProvider(JacksonJsonNodeJsonProvider(objectMapper))
    .options(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL)
    .build()

typealias Document = com.jayway.jsonpath.DocumentContext
typealias Node = com.fasterxml.jackson.databind.JsonNode

fun Node.toDocument(): Document {
    return JsonPath.using(jsonPathCfg).parse(this)
}

fun Map<String, Any>.toNode(): Node {
    return objectMapper.valueToTree(this)
}

inline fun <reified T> Document.evaluate(path: String): T {
    return read(path)
}

/**
 * Represents a variable which refer to a JSON object or array.
 *
 * @param context The evaluated JSON object or array, which is also a JSONPath context.
 */
internal data class Variable(val context: Document) {
    val isArray = context.json<Node>().isArray
}

internal data class VariableTable(
    val global: Variable,
    val variables: Map<String, Variable>
) {

    val root: Variable = this["\$"] ?: error("Root node not found")

    operator fun get(name: String): Variable? = variables[name]

    operator fun plus(pair: Pair<String, Variable>): VariableTable = copy(variables = variables + pair)

    fun evaluate(path: String): List<Node> {
        val (context, effectivePath) = resolveEvaluationContext(path)

        return try {
            val result = context.read<Node>(effectivePath)
            when {
                result.isArray -> result.elements().asSequence().toList()
                else -> listOf(result)
            }
        } catch (e: Exception) {
            listOf(TextNode.valueOf(""))
        }
    }

    private fun resolveEvaluationContext(path: String): Pair<Document, String> {
        if (path.startsWith("$.")) {
            // standard root json path
            return this.global.context to path
        }

        if (path.startsWith(".")) {
            // relative json path to the current object
            return this.root.context to "\$$path"
        }

        // named global variables
        val variableNameRegex = "^\\$(\\w+)".toRegex()
        val match = variableNameRegex.find(path)

        if (match != null) {
            val variableName = "$" + match.groupValues[1]
            val variable = variables[variableName]
                ?: throw IllegalArgumentException("Undefined variable: $variableName")

            val relativePath = path.removePrefix(variableName)

            val effectivePath = if (relativePath.isNotEmpty()) {
                if (variable.isArray) "$[*]$relativePath" else "$$relativePath"
            } else {
                if (variable.isArray) "$[*]" else "$"
            }

            return variable.context to effectivePath
        }

        throw IllegalArgumentException("Invalid path format: $path")
    }

}

internal data class PipemillContext(
    val expression: String,
    val variables: VariableTable
)

internal sealed interface Pipemill {
    val alias: String
        get() = "$"

    fun copy(alias: String): Pipemill
}

internal data class SimplePipemill(
    val path: String,
    private val raw: String = path,
    override val alias: String = "$",
) : Pipemill {
    override fun copy(alias: String): SimplePipemill = copy(path = path, alias = alias)

    override fun toString(): String = raw
}

internal data class ObjectPipemill(
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

fun main(args: Array<String>) {
    exitProcess(CommandLine(JSON2CSVCommand()).execute(*args))
}