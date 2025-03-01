package io.johnsonlee.exec.cmd

import com.google.auto.service.AutoService
import com.microsoft.playwright.options.Cookie
import io.johnsonlee.exec.internal.jackson.objectMapper
import io.johnsonlee.exec.internal.playwright.PlaywrightManager
import io.johnsonlee.exec.internal.playwright.asHttpCookie
import io.johnsonlee.exec.internal.playwright.waitForClose
import java.io.File

@AutoService(Command::class)
class SaveCookiesCommand : IOCommand() {

    override fun run() = PlaywrightManager.newContext().use { context ->
        context.newPage().apply {
            navigate(input)
        }.waitForClose()

        val cookies = context.cookies().map(Cookie::asHttpCookie)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(File(output), cookies)
    }

}