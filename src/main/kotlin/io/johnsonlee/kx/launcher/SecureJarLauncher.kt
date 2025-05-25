package io.johnsonlee.kx.launcher

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

class SecureJarLauncher(
    private val jarFetcher: MavenArtifactFetcher,
    private val groupId: String,
    private val artifactId: String,
    private val version: String = jarFetcher.latestVersion(groupId, artifactId),
    private val classifier: String = "all"
) {

    fun run(args: Array<out String>) {
        val jarUrl = jarFetcher.jarUrl(groupId, artifactId, version, classifier)
        val cachedJarFile = jarFetcher.cachedFile(jarUrl)
        val cachedMd5File = File(cachedJarFile.parent, "${cachedJarFile.name}.md5")
        val cachedSha1File = File(cachedJarFile.parent, "${cachedJarFile.name}.sha1")

        if (cachedMd5File.exists() && cachedJarFile.exists() &&cachedSha1File.exists()) {
            val expectedMd5 = cachedMd5File.readText().trim()
            val expectedSha1 = cachedSha1File.readText().trim()
            val (actualMd5, actualSha1) = cachedJarFile.md5AndSha1()
            if (expectedMd5 == actualMd5 && expectedSha1 == actualSha1) {
                cachedJarFile.runMain(args)
                return
            }
        }

        val jarFile = jarFetcher.fetch(jarUrl, true)
        val (md5, sha1) = jarFile.md5AndSha1()

        jarFetcher.fetch(URL("${jarUrl}.md5"), true).takeIf {
            it.readText().trim() == md5
        } ?: error("Failed to fetch MD5 checksum for $jarFile")

        jarFetcher.fetch(URL("${jarUrl}.sha1"), true).takeIf {
            it.readText().trim() == sha1
        } ?: error("SHA1 checksum mismatch for $jarFile")

        cachedJarFile.runMain(args)
    }

    private fun File.runMain(args: Array<out String>) {
        val mainClassName = JarFile(this).use { jar ->
            jar.manifest.mainAttributes.getValue("Main-Class")
        } ?: error("Main-Class not found in ${this.absolutePath}")
        val classLoader = URLClassLoader(arrayOf(this.toURI().toURL())).also {
            Thread.currentThread().contextClassLoader = it
        }
        val mainClass = classLoader.loadClass(mainClassName)
        mainClass.getMethod("main", Array<String>::class.java).invoke(null, args)
    }

}