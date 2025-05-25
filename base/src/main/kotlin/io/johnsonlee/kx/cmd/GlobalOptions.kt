package io.johnsonlee.kx.cmd

import picocli.CommandLine.Option

class GlobalOptions {

    @Option(names = ["--verbose"], description = ["verbose mode"])
    var verbose: Boolean = false

    fun verbose(message: String) {
        if (!verbose) return
        System.err.println(message)
    }

}