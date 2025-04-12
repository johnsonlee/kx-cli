package io.johnsonlee.exec.cmd

import picocli.CommandLine
import picocli.CommandLine.ParentCommand

abstract class IOCommand : Command {

    @ParentCommand
    lateinit var parent: Exec

    @CommandLine.Parameters(index = "0", description = ["Input URIs"], arity = "1..*")
    lateinit var input: List<String>

    @CommandLine.Option(names = ["-o", "--output"], description = ["Output URI"], defaultValue = "/dev/stdout")
    lateinit var output: String

    @CommandLine.Option(names = ["--debug"], description = ["Debug mode"])
    var debug: Boolean = false

    fun debug(message: String) {
        if (debug) {
            System.err.println(message)
        }
    }

}