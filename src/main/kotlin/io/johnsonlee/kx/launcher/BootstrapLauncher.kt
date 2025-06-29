package io.johnsonlee.kx.launcher

import io.johnsonlee.kx.cmd.GlobalOptions
import io.johnsonlee.kx.internal.VersionProvider
import java.io.File
import java.net.URI
import kotlin.system.exitProcess
import picocli.CommandLine

@CommandLine.Command(
    name = "kx",
    mixinStandardHelpOptions = true,
    description = ["Bootstrap launcher"],
    versionProvider = VersionProvider::class,
)
class BootstrapLauncher(private val args: Array<out String>) : Runnable {

    @CommandLine.Option(
        names = ["--maven"],
        description = ["Maven repository URL"],
        defaultValue = "https://repo1.maven.org/maven2/"
    )
    var maven: String = "https://repo1.maven.org/maven2/"

    @CommandLine.Option(names = ["--module"], description = ["Module name"], required = true)
    lateinit var module: String

    private val cacheDir = File(System.getProperty("user.home"), ".kx-cli").apply(File::mkdirs)

    private val fetcher = MavenArtifactFetcher(URI.create(maven), cacheDir)

    private val groupId: String
        get() = module.substringBefore(":")

    private val artifactId: String
        get() = module.substringAfter(":")

    override fun run() {
        SecureJarLauncher(fetcher, groupId, artifactId).run(args)
    }

}