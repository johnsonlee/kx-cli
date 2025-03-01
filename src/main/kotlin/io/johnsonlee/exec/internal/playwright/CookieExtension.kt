package io.johnsonlee.exec.internal.playwright

import com.microsoft.playwright.options.Cookie
import java.net.HttpCookie
import java.time.Instant

fun Cookie.asHttpCookie(): HttpCookie {
    val cookie = HttpCookie(name, value)
    cookie.domain = domain
    cookie.path = path
    cookie.secure = secure
    cookie.maxAge = expires?.let { Instant.now().epochSecond - it.toLong() } ?: -1
    return cookie
}
