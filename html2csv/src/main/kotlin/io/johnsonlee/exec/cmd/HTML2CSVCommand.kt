package io.johnsonlee.exec.cmd

import com.google.auto.service.AutoService
import io.johnsonlee.exec.internal.capitalize
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import picocli.CommandLine
import picocli.CommandLine.Option

/**
 * Converts HTML into CSV with flexible row selection and powerful column extraction capabilities.
 *
 * ## Overview
 *
 * This command reads a HTML file, selects rows using a XPath expression,
 * and extracts columns using a flexible **column definition DSL**.
 *
 * Each row becomes a line in the CSV output, and column values are extracted from each row object
 * based on the following supported column definition syntax.
 *
 * ## Supported Column Definition Features
 *
 * ### 1. Standard XPath
 *
 * Each column can extract its value using **standard XPath expressions**, either absolute or relative.
 *
 * Example (absolute path):
 * ```
 * /html/body/table/tr/td[1]
 * ```
 *
 * Example (relative path inside row):
 * ```
 * ./table/tr/td[1]
 * ```
 *
 * ### 2. Pipeline Processing (Chained Filters)
 *
 * Columns can apply **pipeline processing**, where multiple steps process the extracted data.
 * Each step receives the result of the previous step.
 *
 * Example:
 * ```
 * /html/body/table/tbody/tr | {id:td[1], name:td[2]}
 * ```
 *
 * ### 3. Object Definition Columns
 *
 * Columns can be **composite objects**, combining multiple fields into a single JSON object.
 *
 * Example:
 * ```
 * /html/body/table/tbody/tr | {id:td[1], name:td[2], status:td[3]}
 * ```
 *
 * ### 4. Named Global Variables
 * Named variables can be dynamically created within pipelines using the `as $variableName` syntax.
 *
 * Example:
 * ```
 * /html/body/table/tbody/tr as $items
 * ```
 * This defines `$items`, which can then be referenced later:
 * ```
 * $items/td[1]
 * ```
 * Named variables can be used to store intermediate results, making complex pipelines more readable.
 * Example:
 * ```
 * /html/body/table/tbody/tr as $item | {id:$item/td[1], name:$item/td[2]}
 * ```
 *
 * Named variables:
 * - Must start with `$`
 * - Can be objects, arrays, or primitive values
 * - Are scoped to the current row
 *
 * ### 5. Simple XPath (Relative Paths)
 *
 * Column paths can be simple relative paths if they refer to fields directly under the current row object.
 *
 * Example:
 * ```
 * order_id:td[1]
 * ```
 * This extracts `order_id` from each row object directly.
 *
 * ## Array Handling
 *
 * When a column maps to an array, each element becomes a separate row.
 * When multiple columns map to arrays, a **Cartesian product** of all arrays is produced.
 *
 * ## Example Command
 *
 * ```
 * java -jar exec.jar exec html2csv \
 *     --input input.json \
 *     --output output.csv \
 *     --row '/html/body/table/tbody/tr/td' \
 *     --header id,name \
 *     --delimiter ','
 * ```
 *
 * ## Options
 *
 * - `--input`: Path to the input JSON file.
 * - `--output`: Path to the output CSV file.
 * - `--row`: XPath expression selecting each row object.
 * - `--header`: List of column names, in order.
 * - `--delimiter`: Column delimiter (default is `,`).
 *
 * For further details and examples, see project documentation.
 */
@AutoService(Command::class)
class HTML2CSVCommand : FetchCommand() {

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
        val input = Variable(Element("A").attr("href", input).text(input))
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
                    it?.text()?.trim()?.let { text ->
                        "$textQualifier${text}$textQualifier"
                    } ?: ""
                })
            }
        }
    }

    private fun loadDocument(input: String): Document {
        return if (input.startsWith("http://") || input.startsWith("https://")) {
            val response = get(url())
            if (response.isSuccessful) {
                response.body?.let { body ->
                    body.byteStream().use {
                        Jsoup.parse(it, (body.contentType()?.charset() ?: StandardCharsets.UTF_8).name(), input)
                    }
                } ?: error("Loading JSON from $input failed")
            } else {
                error("Loading JSON from $input failed: ${response.code} ${response.message}")
            }
        } else {
            Jsoup.parse(File(input))
        }
    }

    private fun evaluateRowExpression(expression: String, variables: VariableTable): Sequence<List<Node?>> {
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

    private fun evaluatePipeline(currentNodes: List<PipemillContext>, steps: List<Pipemill>, stepIndex: Int = 0): Sequence<List<Node?>> {
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
            val newVariables = pipeline.variables + (step.alias to Variable(node as Element))
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
            listOf(nodes.mapValues { it.value.firstOrNull() }.toMap().toNode())
        } else {
            val staticValues = nodes.filterValues { it.size <= 1 }
                .mapValues { it.value.firstOrNull() }
            val arrayValues = nodes.filterValues { it.size > 1 }

            cartesianProduct(arrayValues).map {
                staticValues + it
            }.map {
                it.toNode()
            }
        }

        return rows.map { row ->
            val newVariables = pipeline.variables + (step.alias to Variable(row as Element))
            PipemillContext(step.expression, newVariables)
        }
    }

    private fun evaluatePipemill(pipemill: Pipemill, variables: VariableTable): Sequence<List<Node?>> = when (pipemill) {
        is SimplePipemill -> expandSimplePipemill(pipemill, variables)
        is ObjectPipemill -> expandObjectPipemill(pipemill, variables)
    }

    private fun expandObjectPipemill(pipemill: ObjectPipemill, variables: VariableTable): Sequence<List<Node?>> = sequence {
        val extractedValues = pipemill.objectDefinition.mapValues { (_, path) ->
            variables.evaluate(path)
        }

        val rows = if (extractedValues.values.all { it.size <= 1 }) {
            listOf(extractedValues.values.map(List<Node>::firstOrNull))
        } else {
            val staticValues = extractedValues.filterValues {
                it.size <= 1
            }.mapValues {
                it.value.firstOrNull()
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

typealias Document = org.jsoup.nodes.Document
typealias Node = org.jsoup.nodes.Node

fun Node.text(): String {
    val builder = StringBuilder()
    NodeTraversor.traverse(object : NodeVisitor {
        override fun head(node: Node, depth: Int) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> {
                    if (builder.isNotEmpty() && (node.isBlock || node.nameIs("br")) && builder.lastOrNull() != ' ') {
                        builder.append(' ')
                    }
                }
            }
        }

        override fun tail(node: org.jsoup.nodes.Node, depth: Int) {
            if (node !is Element) return

            val next = node.nextSibling()
            if (node.isBlock && (next is TextNode || next is Element && !next.isBlock) && builder.lastOrNull() != ' ') {
               builder.append(' ')
            }
        }

    }, this)
    return builder.toString()
}

fun Map<String, Any?>.toNode(): Node {
    val element = Element("virtual")
    forEach { (key, value) ->
        element.attr(key, value.toString())
    }
    return element
}

fun Element.evaluate(path: String): List<Element> {
    return selectXpath(path)
}

internal data class Variable(val context: Element)

internal data class VariableTable(
    val global: Variable,
    val variables: Map<String, Variable>
) {

    val root: Variable = this["\$"] ?: error("Root node not found")

    operator fun get(name: String): Variable? = variables[name]

    operator fun plus(pair: Pair<String, Variable>): VariableTable = copy(variables = variables + pair)

    fun evaluate(path: String): List<Node> {
        val (context, effectivePath) = resolveEvaluationContext(path)
        return context.evaluate(effectivePath)
    }

    private fun resolveEvaluationContext(path: String): Pair<Element, String> {
        if (path.startsWith("/")) {
            // standard root json path
            return this.global.context to path
        }

        if (path.startsWith("./")) {
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
            val effectivePath = path.removePrefix(variableName).removePrefix("/")
            return variable.context to effectivePath
        }

        return this.root.context to path
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
    exitProcess(CommandLine(HTML2CSVCommand()).execute(*args))
}