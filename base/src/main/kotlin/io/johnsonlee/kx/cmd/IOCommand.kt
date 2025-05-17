package io.johnsonlee.kx.cmd

import picocli.CommandLine
import picocli.CommandLine.ParentCommand

abstract class IOCommand : Command {

    @ParentCommand
    var parent: Kx? = null

    @CommandLine.Parameters(index = "0", description = ["URIs to read or `-` for stdin"], arity = "1..*")
    lateinit var input: List<String>

    @CommandLine.Option(names = ["-o", "--output"], description = ["Output URI"], defaultValue = "/dev/stdout")
    lateinit var output: String

    override fun verbose(message: String) {
        parent?.verbose(message)
    }

}