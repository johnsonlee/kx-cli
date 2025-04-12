package io.johnsonlee.exec.cmd

data class PipemillContext(
    val expression: String,
    val variables: VariableTable
)

sealed interface Pipemill {
    val alias: String
        get() = "$"

    fun copy(alias: String): Pipemill
}

/**
 * Represents a variable which refer to a DOM element
 *
 * @param context The DOM document
 */
data class Variable(val context: DOMContext)

data class VariableTable(
    val global: Variable,
    val variables: Map<String, Variable>
) {

    val root: Variable = this["\$"] ?: error("Root node not found")

    operator fun get(name: String): Variable? = variables[name]

    operator fun plus(pair: Pair<String, Variable>): VariableTable = copy(variables = variables + pair)

    fun evaluate(path: String): List<DOMNode> = root.context.evaluate(path, this)

}
