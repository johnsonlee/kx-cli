package io.johnsonlee.kx.cmd

import com.google.auto.service.AutoService
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import picocli.CommandLine

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
class HTML2CSVCommand : DOM2CSVCommand() {

    override fun parse(input: String): DOMContext = get(input) {
        parse(it, null, input)
    }

    override fun parse(input: InputStream, location: String): DOMContext = parse(input, null, location)

    private fun parse(input: InputStream, charset: Charset?, location: String): DOMContext {
        val doc = Jsoup.parse(input, (charset ?: StandardCharsets.UTF_8).name(), location)
        return XmlDOMContext(doc)
    }
}

class XmlDOMNode(
    node: Node,
    override val context: DOMContext = XmlDOMContext(node as Element)
): DOMNode {
    override val text: String = node.text()
    override val isIterable: Boolean = node is Element
    override val children: List<DOMNode> = (node as Element).children().map(::XmlDOMNode)
}

class XmlDOMContext(
    internal val context: Element
) : DOMContext {

    override val root: DOMNode
        get() = XmlDOMNode(context, this)

    override fun evaluate(path: String, variables: VariableTable): List<DOMNode> {
        return XPath(path).evaluate(variables)
    }

    override fun newTextNode(value: String): DOMNode {
        return XmlDOMNode(Element("DIV").text(value))
    }

    override fun newNode(attrs: Map<String, Any>): DOMNode {
        val element = Element("virtual")
        attrs.forEach { (key, value) ->
            element.attr(key, value.toString())
        }
        return XmlDOMNode(element)
    }

}

class XPath(override val path: String) : DOMPath {

    override fun evaluate(variables: VariableTable): List<DOMNode> {
        val (doc, effectivePath) = resolveEvaluationContext(path, variables)
        return (doc as XmlDOMContext).context.selectXpath(effectivePath).map(::XmlDOMNode)
    }

    private fun resolveEvaluationContext(path: String, variables: VariableTable): Pair<DOMContext, String> {
        if (path.startsWith("/")) {
            // standard root json path
            return variables.global.context to path
        }

        if (path.startsWith("./")) {
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
            val effectivePath = path.removePrefix(variableName).removePrefix("/")
            return variable.context to effectivePath
        }

        return variables.root.context to path
    }
}

fun org.jsoup.nodes.Node.text(): String {
    val builder = StringBuilder()
    NodeTraversor.traverse(object : NodeVisitor {
         override fun head(node: org.jsoup.nodes.Node, depth: Int) {
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

fun main(args: Array<String>) {
    exitProcess(CommandLine(HTML2CSVCommand()).execute(*args))
}