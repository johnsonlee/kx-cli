package io.johnsonlee.kx.launcher

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

class MavenArtifactFetcher(
    private val mavenUri: URI,
    private val cacheDir: File,
) {

    private val saxParserFactory = SAXParserFactory.newInstance()

    private val saxParser = saxParserFactory.newSAXParser()

    private val baseUrl = mavenUri.toString().removeSuffix("/")

    val mavenUrl: URL = URL("${baseUrl}/")

    init {
        cacheDir.mkdirs()
    }

    private fun metadataUrl(groupId: String, artifactId: String): URL {
        return URL("${baseUrl}/${groupId.replace('.', '/')}/${artifactId}/maven-metadata.xml")
    }

    private fun artifactPath(groupId: String, artifactId: String, version: String, classifier: String = "all"): String {
        val prefix = listOf(groupId.replace('.', '/'), artifactId, version).joinToString("/")
        val suffix = listOf(artifactId, version, classifier).filter(String::isNotBlank).joinToString("-")
        return "${prefix}/${suffix}"
    }

    fun jarUrl(groupId: String, artifactId: String, version: String, classifier: String = "all"): URL {
        return URL("${baseUrl}/${artifactPath(groupId, artifactId, version, classifier)}.jar")
    }

    fun fetch(url: URL, forceUpdate: Boolean = true): File {
        val file = cachedFile(url).also { it.parentFile.mkdirs() }

        if (!forceUpdate && file.exists()) {
            System.err.println("Using cached file: $file")
            return file
        }

        retry(5) { n ->
            System.err.println("Downloading${if (n > 0) " [retry $n]" else ""} $url => $file")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setUserAgent()
                connect()
            }

            file.outputStream().buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    input.copyTo(output)
                }
            }
        }

        return file.takeIf(File::exists) ?: throw IOException("Failed to download file: $url")
    }

    fun latestVersion(groupId: String, artifactId: String): String {
        val metadataUrl = metadataUrl(groupId, artifactId)

        return retry(5) {
            val handler = MavenMetadataHandler()
            val connection = (metadataUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setUserAgent()
                setRequestProperty("Accept", "application/xml")
                connect()
            }

            System.err.println("Fetching $metadataUrl")

            try {
                connection.inputStream.buffered().use {
                    saxParser.parse(it, handler)
                }
                null
            } catch (e: EarlyExitException) {
                handler.latestVersion
            } ?: throw IllegalStateException("No latest version found: $metadataUrl")
        }
    }

    private fun HttpURLConnection.setUserAgent() {
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        )
    }

    private fun <T> retry(
        maxAttempts: Int = 3,
        delayMillis: Long = 1000,
        block: (Int) -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return block(attempt)
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxAttempts - 1 && delayMillis > 0) {
                    Thread.sleep(delayMillis * attempt)
                }
            }
        }

        throw lastException ?: error("Unknown error")
    }

    fun cachedFile(url: URL): File {
        if (!url.toString().startsWith(mavenUrl.toString())) {
            throw IllegalArgumentException("URL must start with the Maven base URL: $mavenUrl")
        }

        val cachePath = mavenUrl.toURI().relativize(url.toURI()).toString()
        return File(cacheDir, if ("/" != File.separator) cachePath.replace("/", File.separator) else cachePath)
    }
}

class MavenMetadataHandler : DefaultHandler() {
    private var inLatest = false
    private var version: String? = null

    val latestVersion: String?
        get() = version

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        if (qName == TAG_LATEST) inLatest = true
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inLatest && version == null) {
            version = String(ch, start, length)
            throw EarlyExitException()
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        if (qName == TAG_LATEST) inLatest = false
    }

    companion object {
        private const val TAG_LATEST = "latest"
    }
}

class EarlyExitException : SAXException()
