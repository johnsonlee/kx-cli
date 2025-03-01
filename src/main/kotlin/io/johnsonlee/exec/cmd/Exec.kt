package io.johnsonlee.exec.cmd

import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "exec", mixinStandardHelpOptions = true, version = ["1.0"])
class Exec : Runnable {

    @Option(names = ["-v", "--verbose"], description = ["Verbose mode"])
    var verbose: Boolean = false

    override fun run() {
        println("Please specify a subcommand")
    }

}