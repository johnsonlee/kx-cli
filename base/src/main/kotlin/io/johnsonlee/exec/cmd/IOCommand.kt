package io.johnsonlee.exec.cmd

import picocli.CommandLine
import picocli.CommandLine.ParentCommand

abstract class IOCommand : Command {

    @ParentCommand
    lateinit var parent: Exec

    @CommandLine.Parameters(index = "0", description = ["Input URI"])
    lateinit var input: String

    @CommandLine.Option(names = ["-o", "--output"], description = ["Output URI"], defaultValue = "/dev/stdout")
    lateinit var output: String

}