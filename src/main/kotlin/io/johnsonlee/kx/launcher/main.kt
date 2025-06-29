package io.johnsonlee.kx.launcher

import io.johnsonlee.kx.cmd.GlobalOptions
import kotlin.system.exitProcess
import picocli.CommandLine

fun main(vararg args: String) {
    val launcherArgs = args.filter {
        it.startsWith("--maven=") || it.startsWith("--module=") || it == "--help" || it == "-h"
    }.toSet()
    val appArgs = (args.toSet() - launcherArgs).toTypedArray()
    val cmdline = CommandLine(BootstrapLauncher(appArgs)).addMixin("global", GlobalOptions())
    exitProcess(cmdline.execute(*launcherArgs.toTypedArray()))
}