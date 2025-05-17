package io.johnsonlee.kx.cmd

import io.johnsonlee.kx.internal.VersionProvider
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "kx",
    mixinStandardHelpOptions = true,
    description = ["A flexible and extensible command line CLI"],
    versionProvider = VersionProvider::class,
)
class Kx : Runnable {

    @Option(names = ["--verbose"], description = ["verbose mode"])
    var verbose: Boolean = false

    override fun run() {
        System.err.println("Please specify a subcommand")
    }

    fun verbose(message: String) {
        if (!verbose) return
        System.err.println(message)
    }

}