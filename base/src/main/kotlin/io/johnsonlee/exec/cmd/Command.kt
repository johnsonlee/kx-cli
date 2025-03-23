package io.johnsonlee.exec.cmd

interface Command : Runnable {

    /**
     * The name of subcommand
     */
    val name: String
        get() = this.javaClass.simpleName.substringBeforeLast("Command").replace(Regex("([a-z])([A-Z])")) {
            "${it.groupValues[1]}-${it.groupValues[2].lowercase()}"
        }.lowercase()

}