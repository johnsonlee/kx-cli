package io.johnsonlee.kx.cmd

import com.microsoft.playwright.options.Cookie
import io.johnsonlee.kx.internal.VersionProvider
import io.johnsonlee.kx.internal.playwright.asHttpCookie
import java.io.File
import picocli.CommandLine

@CommandLine.Command(
    name = "save-cookie",
    description = ["Save cookies to file"],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class SaveCookie : Command {

    @CommandLine.ParentCommand
    lateinit var parent: BrowserCommand

    @CommandLine.Option(names = ["--cookie-file"], description = ["cookie file to save"], defaultValue = "/dev/stdout")
    lateinit var cookieFile: String

    override fun verbose(message: String) = parent.verbose(message)

    override fun run() = parent.run { context ->
        context.cookies().map(Cookie::asHttpCookie).let { cookies ->
            verbose("Saving cookies to $cookieFile")

            val json = cookies.joinToString(",", prefix = "[", postfix = "]") {
                """
                {
                    "name": "${it.name}",
                    "value": "${it.value}",
                    "domain": "${it.domain}",
                    "path": "${it.path}",
                    "maxAge": ${it.maxAge},
                    "httpOnly": ${it.isHttpOnly},
                    "secure": ${it.secure}
                }
                """.trimIndent()
            }

            File(cookieFile).also {
                it.parentFile.mkdirs()
            }.writeText(json)
        }
    }

}