package io.johnsonlee.kx

import io.johnsonlee.kx.cmd.Command
import io.johnsonlee.kx.cmd.Kx
import java.util.ServiceLoader
import kotlin.system.exitProcess
import picocli.CommandLine

fun main(array: Array<String>) {
    val cmdline = CommandLine(Kx())

    ServiceLoader.load(Command::class.java).forEach {
        cmdline.addSubcommand(it.name, it)
    }

    exitProcess(cmdline.execute(*array))
}