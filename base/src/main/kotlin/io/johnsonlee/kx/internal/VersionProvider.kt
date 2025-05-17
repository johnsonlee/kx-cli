package io.johnsonlee.kx.internal

import java.util.Properties
import picocli.CommandLine.IVersionProvider

class VersionProvider : IVersionProvider {

    private val properties = Properties()

    private val version: String?
        get() = properties.getProperty("version")

    private val revision: String?
        get() = properties.getProperty("revision")

    init {
        javaClass.getResourceAsStream("/version.properties")?.use(properties::load)
    }

    override fun getVersion(): Array<String> {
        val v = if (version.isNullOrBlank() && revision.isNullOrBlank()) {
            "unknown"
        } else if (version.isNullOrBlank()) {
            revision.toString()
        } else if (revision.isNullOrBlank()) {
            version.toString()
        } else "$version ($revision)"

        return arrayOf(v)
    }
}