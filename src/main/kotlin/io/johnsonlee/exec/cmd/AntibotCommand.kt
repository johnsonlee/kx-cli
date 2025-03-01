package io.johnsonlee.exec.cmd

import com.google.auto.service.AutoService
import io.johnsonlee.exec.internal.playwright.PlaywrightManager
import io.johnsonlee.exec.internal.playwright.waitForClose
import picocli.CommandLine
import picocli.CommandLine.ParentCommand

@AutoService(Command::class)
class AntibotCommand : Command {

    @ParentCommand
    lateinit var parent: Exec

    @CommandLine.Option(names = ["-d", "--detector"], description = ["Detection provider"], defaultValue = "https://bot.sannysoft.com/")
    lateinit var detector: String

    override fun run() = PlaywrightManager.newContext().use { context ->
        context.newPage().apply {
            navigate(detector)
        }.waitForClose()
    }
}