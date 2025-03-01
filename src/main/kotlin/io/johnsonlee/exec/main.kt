package io.johnsonlee.exec

import io.johnsonlee.exec.cmd.Exec
import io.johnsonlee.exec.cmd.Command
import picocli.CommandLine
import java.util.*
import kotlin.system.exitProcess

fun main(array: Array<String>) {
    val cmdline = CommandLine(Exec())

    ServiceLoader.load(Command::class.java).forEach {
        cmdline.addSubcommand(it.name, it)
    }

    exitProcess(cmdline.execute(*array))
}