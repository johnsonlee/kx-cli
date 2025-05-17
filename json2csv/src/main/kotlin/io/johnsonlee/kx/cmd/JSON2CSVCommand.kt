package io.johnsonlee.kx.cmd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.TextNode
import com.google.auto.service.AutoService
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
import io.johnsonlee.kx.cmd.JSON2CSVCommand.Companion.objectMapper
import java.io.InputStream
import kotlin.system.exitProcess
import picocli.CommandLine

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
open class JSON2CSVCommand : DOM2CSVCommand() {

    companion object {
        val objectMapper = ObjectMapper().apply {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    override fun parse(input: InputStream, location: String): DOMContext = JsonDOMContext(objectMapper.readTree(input))

}

private val jsonPathCfg = Configuration.builder()
    .jsonProvider(JacksonJsonNodeJsonProvider(objectMapper))
    .options(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL)
    .build()

class JsonDOMNode(
    internal val node: JsonNode,
    override val context: DOMContext = JsonDOMContext(node)
) : DOMNode {
    override val text: String = node.asText()
    override val isIterable: Boolean = node.isArray
    override val children: List<DOMNode> = node.elements().asSequence().map(::JsonDOMNode).toList()
}

class JsonDOMContext(node: JsonNode) : DOMContext {

    internal val context: DocumentContext = JsonPath.using(jsonPathCfg).parse(node)

    override val root: DOMNode = JsonDOMNode(context.json(), this)

    override fun evaluate(path: String, variables: VariableTable): List<DOMNode> {
       return JsonPath(path).evaluate(variables)
    }

    override fun newTextNode(value: String): DOMNode = JsonDOMNode(TextNode.valueOf(value))

    override fun newNode(attrs: Map<String, Any>): DOMNode = attrs.map { (key, value) ->
        key to when (value) {
            is JsonDOMNode -> value.node
            else -> value
        }
    }.toMap().let<Map<String, Any>, JsonNode>(objectMapper::valueToTree).let(::JsonDOMNode)

}

private class JsonPath(override val path: String) : DOMPath {
    override fun evaluate(variables: VariableTable): List<DOMNode> {
        val (context, effectivePath) = resolveEvaluationContext(variables, path)
        val result = (context as JsonDOMContext).context.read<JsonNode>(effectivePath)
        return when {
            result.isArray -> result.elements().asSequence().map(::JsonDOMNode).toList()
            else -> listOf(JsonDOMNode(result))
        }
    }

    private fun resolveEvaluationContext(variables: VariableTable, path: String): Pair<DOMContext, String> {
        if (path.startsWith("$.")) {
            // standard root json path
            return variables.global.context to path
        }

        if (path.startsWith(".")) {
            // relative json path to the current object
            return variables.root.context to "\$$path"
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
                if (variable.context.root.isIterable) "$[*]$relativePath" else "$$relativePath"
            } else {
                if (variable.context.root.isIterable) "$[*]" else "$"
            }

            return variable.context to effectivePath
        }

        throw IllegalArgumentException("Invalid path format: $path")
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(JSON2CSVCommand()).execute(*args))
}