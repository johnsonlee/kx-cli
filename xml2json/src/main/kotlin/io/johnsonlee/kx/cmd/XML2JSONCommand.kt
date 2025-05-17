package io.johnsonlee.kx.cmd

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.io.File
import java.io.OutputStream
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

class XML2JSONCommand : FetchCommand() {

    companion object {
        private val xmlMapper = XmlMapper()
        private val objectMapper = ObjectMapper()
        private val jsonFactory = JsonFactory()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun run() = runBlocking {
        val nodes = input.asFlow().flatMapMerge { url ->
            flow {
                emit(get<JsonNode?>(url, xmlMapper::readTree)?.fixEmptyKeys())
            }
        }.flowOn(Dispatchers.IO).toList().filterNotNull().takeIf {
            it.isNotEmpty()
        } ?: return@runBlocking

        File(output).outputStream().buffered().use { out ->
            when (nodes.size) {
                1 -> nodes.first()
                else -> objectMapper.createArrayNode().apply {
                    addAll(nodes)
                }
            }.write(out)
        }
    }

    private fun JsonNode.write(out: OutputStream) {
        jsonFactory.createGenerator(out).use { generator ->
            generator.useDefaultPrettyPrinter()
            generator.codec = objectMapper
            generator.writeTree(this)
        }
    }

}

private fun JsonNode.fixEmptyKeys(): JsonNode = apply {
    when (val node = this) {
        is ObjectNode -> {
            val fields = node.fields().asSequence().toList()
            for ((key, value) in fields) {
                if (key == "") {
                    node.remove("")
                    val newKey = if (value is TextNode) "#text" else "value"
                    node.set<ObjectNode>(newKey, value.fixEmptyKeys())
                } else {
                    node.set<ObjectNode>(key, value.fixEmptyKeys())
                }
            }
        }

        is ArrayNode -> {
            for (i in 0 until node.size()) {
                node.set(i, node[i].fixEmptyKeys())
            }
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(XML2JSONCommand()).execute(*args))
}