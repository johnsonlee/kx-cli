package io.johnsonlee.exec.cmd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.addMixIn
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auto.service.AutoService
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.Cookie
import io.johnsonlee.exec.internal.network.HttpCookieMixin
import io.johnsonlee.exec.internal.playwright.PlaywrightManager
import io.johnsonlee.exec.internal.playwright.asHttpCookie
import io.johnsonlee.exec.internal.playwright.waitForClose
import java.io.File
import java.net.HttpCookie
import kotlin.system.exitProcess
import picocli.CommandLine

@AutoService(Command::class)
class SaveCookiesCommand : IOCommand() {

    companion object {
        val objectMapper = jacksonObjectMapper().apply {
            registerKotlinModule()
            registerModules(JavaTimeModule())
            addMixIn<HttpCookie, HttpCookieMixin>()
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    override fun run() = PlaywrightManager.newContext().use { context ->
        input.map { url ->
            context.newPage().apply {
                navigate(url)
            }
        }.onEach(Page::waitForClose)

        val cookies = context.cookies().map(Cookie::asHttpCookie)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(File(output), cookies)
    }

}

fun main(args: Array<String>) {
    exitProcess(CommandLine(SaveCookiesCommand()).execute(*args))
}