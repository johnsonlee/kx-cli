package io.johnsonlee.kx.internal.playwright

import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.impl.TargetClosedError
import java.net.URL

fun Page.waitForClose() = try {
    waitForCondition(this::isClosed, Page.WaitForConditionOptions().setTimeout(0.0))
} catch (_: TargetClosedError) {
}

fun BrowserContext.newPage(url: String): Page = newPage().apply {
    navigate(url)
}

fun BrowserContext.newPage(url: URL): Page = newPage(url.toString())
