package io.johnsonlee.kx.cmd

import com.google.auto.service.AutoService
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import io.johnsonlee.kx.internal.VersionProvider
import io.johnsonlee.kx.internal.playwright.PlaywrightManager
import io.johnsonlee.kx.internal.playwright.waitForClose
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.ParentCommand

@AutoService(Command::class)
@CommandLine.Command(
    name = "browser",
    mixinStandardHelpOptions = true,
    description = ["browser engine for automation"],
    versionProvider = VersionProvider::class,
    subcommands = [SaveCookie::class]
)
class BrowserCommand : Command {

    @ParentCommand
    var parent: Kx? = null

    @CommandLine.Parameters(index = "0", description = ["URLs"], arity = "1..*")
    lateinit var input: List<String>

    override fun verbose(message: String) {
        parent?.verbose(message)
    }

    override fun run() = Unit

    internal fun run(block: (BrowserContext) -> Unit) {
        PlaywrightManager.newContext().use { context ->
            input.map { url ->
                context.newPage().apply {
                    navigate(url)
                }
            }.onEach(Page::waitForClose)

            block(context)
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(BrowserCommand()).execute(*args))
}