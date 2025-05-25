package io.johnsonlee.kx.cmd

import picocli.CommandLine
import picocli.CommandLine.Mixin

abstract class IOCommand : Command {

    @Mixin
    lateinit var global: GlobalOptions

    @CommandLine.Parameters(index = "0", description = ["URIs to read or `-` for stdin"], arity = "1..*")
    lateinit var input: List<String>

    @CommandLine.Option(names = ["-o", "--output"], description = ["Output URI"], defaultValue = "/dev/stdout")
    lateinit var output: String

    override fun verbose(message: String) {
        global.verbose(message)
    }

}