package io.johnsonlee.kx.cmd

import java.io.InputStream

interface DOMParser {
    fun parse(input: String): DOMContext
    fun parse(input: InputStream, location: String): DOMContext
}

interface DOMContext {
    val root: DOMNode
    fun evaluate(path: String, variables: VariableTable): List<DOMNode>
    fun newTextNode(value: String): DOMNode
    fun newNode(attrs: Map<String, Any>): DOMNode
    fun newNode(vararg attrs: Pair<String, Any>): DOMNode = newNode(attrs.toMap())
}

interface DOMNode {
    val context: DOMContext
    val text: String
    val isIterable: Boolean
    val children: List<DOMNode>
}

interface DOMPath {
    val path: String
    fun evaluate(variables: VariableTable): List<DOMNode>
}
