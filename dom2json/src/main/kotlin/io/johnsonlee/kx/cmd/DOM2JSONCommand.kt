package io.johnsonlee.kx.cmd

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.google.auto.service.AutoService
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.nodes.LeafNode
import org.jsoup.nodes.Node
import org.jsoup.nodes.XmlDeclaration
import picocli.CommandLine
import picocli.CommandLine.Option

@AutoService(Command::class)
class DOM2JSONCommand : FetchCommand() {

    companion object {
        private val xmlMapper = XmlMapper()
        private val objectMapper = ObjectMapper()
        private val jsonFactory = JsonFactory()
        private val jsonPathCfg = Configuration.builder()
            .jsonProvider(JacksonJsonNodeJsonProvider(objectMapper))
            .options(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build()
    }

    @Option(names = ["--json-path"], description = ["JSON path to query"])
    lateinit var jsonPath: String

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun run() = runBlocking {
        val nodes = input.asFlow().flatMapMerge { url ->
            flow {
                val json = get(url) { mediaType, it ->
                    val charset = mediaType?.charset()?.name() ?: "UTF-8"
                    when (mediaType.toString()) {
                        "text/html" -> Jsoup.parse(it, charset, url).normalize().root().toJsonNode()
                        "application/atom+xml",
                        "application/xml",
                        "text/xml" -> xmlMapper.readTree(it).fixEmptyKeys()
                        else -> error("Unsupported media type: $mediaType")
                    }
                }
                if (::jsonPath.isInitialized) {
                    emit(JsonPath.using(jsonPathCfg).parse(json).read(jsonPath))
                } else {
                    emit(json)
                }
            }
        }.flowOn(Dispatchers.IO).toList().takeIf {
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

    private fun Document.normalize(): Document = apply {
        select("[href]").forEach { element ->
            element.attr("abs:href").takeIf { it.isNotBlank() }?.let { href ->
                element.attr("abs:href", href)
            }
        }
        select("[src]").forEach { element ->
            element.absUrl("src").takeIf { it.isNotBlank() }?.let { src ->
                element.attr("abs:src", src)
            }
        }
    }

    private fun Node.toJsonNode(): JsonNode {
        val tree = objectMapper.createObjectNode()

        if (attributes().size() > 0) {
            for (attr in attributes()) {
                tree.put("@${attr.key}", attr.value)
            }
        }

        val childrenByTag = mutableMapOf<String, MutableList<JsonNode>>()

        for (child in childNodes()) {
            val children = childrenByTag.getOrPut(child.nodeName(), ::mutableListOf)
            when (child) {
                is Element -> children.add(child.toJsonNode())
                is LeafNode -> when (child) {
                    is XmlDeclaration, is DocumentType, is Comment -> Unit
                    else -> child.attr(child.nodeName()).takeIf(String::isNotBlank)?.let {
                        children.add(TextNode.valueOf(it))
                    }
                }
                else -> children.add(child.toJsonNode())
            }
        }

        for ((tag, items) in childrenByTag) {
            if (items.size == 1) {
                tree.set<JsonNode>(tag, items[0])
            } else if (items.size > 1) {
                tree.set<ArrayNode>(tag, objectMapper.createArrayNode().addAll(items))
            }
        }

        return tree
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
    exitProcess(CommandLine(DOM2JSONCommand()).execute(*args))
}