package io.johnsonlee.exec

import io.johnsonlee.exec.cmd.Command
import io.johnsonlee.exec.cmd.Exec
import java.util.ServiceLoader
import kotlin.system.exitProcess
import picocli.CommandLine

fun main(array: Array<String>) {
    val cmdline = CommandLine(Exec())

    ServiceLoader.load(Command::class.java).forEach {
        cmdline.addSubcommand(it.name, it)
    }

    exitProcess(cmdline.execute(*array))
}