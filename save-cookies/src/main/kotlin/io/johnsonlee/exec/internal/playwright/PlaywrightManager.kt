package io.johnsonlee.exec.internal.playwright

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Playwright
import java.awt.GraphicsEnvironment

object PlaywrightManager {

    private val playwright: Playwright by lazy(Playwright::create)

    private val browser by lazy {
        val options = LaunchOptions()
            .setHeadless(GraphicsEnvironment.isHeadless())
            .setArgs(listOf("--disable-blink-features=AutomationControlled", "--start-maximized"))
        playwright.chromium().launch(options).also { browser ->
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    browser.close()
                    playwright.close()
                } catch (_: Throwable) {
                }
            })
        }
    }

    private val contextHolder = ThreadLocal.withInitial {
       browser.newContext(Browser.NewContextOptions().setViewportSize(null))
    }

    fun newContext(): BrowserContext = contextHolder.get().apply {
        addInitScript(
            """
            // pass the webdriver detection
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => parameters.name === 'notifications' ? Promise.resolve({ state: 'denied' }) : originalQuery(parameters);
            HTMLMediaElement.prototype.canPlayType = () => type === 'video/mp4; codecs="avc1.42E01E" ? 'probably': '';
            MediaSource.isTypeSupported = () => type.includes('avc1');
            
            // support mobile device
            const meta = document.createElement('meta');
            meta.name = 'viewport';
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
            document.head.appendChild(meta);
            """
        )
    }

}