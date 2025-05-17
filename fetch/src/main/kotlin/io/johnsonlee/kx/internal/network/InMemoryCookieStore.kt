package io.johnsonlee.kx.internal.network

import okio.withLock
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.locks.ReentrantLock

/**
 * A simple in-memory java.net.CookieStore implementation
 */
class InMemoryCookieStore : CookieStore {
    private val lock = ReentrantLock()
    private val cookieJar = mutableListOf<HttpCookie>()
    private val domainIndex = mutableMapOf<String, MutableList<HttpCookie>>()
    private val uriIndex = mutableMapOf<URI, MutableList<HttpCookie>>()

    override fun add(uri: URI?, cookie: HttpCookie?) {
        requireNotNull(cookie) { "cookie is null" }

        lock.withLock {
            cookieJar.remove(cookie)
            if (cookie.maxAge != 0L) {
                cookieJar.add(cookie)
                cookie.domain?.let { addIndex(domainIndex, it, cookie) }
                uri?.let { addIndex(uriIndex, getEffectiveURI(it), cookie) }
            }
        }
    }

    override fun get(uri: URI?): List<HttpCookie> {
        requireNotNull(uri) { "uri is null" }

        val cookies = mutableListOf<HttpCookie>()
        val secureLink = "https".equals(uri.scheme, ignoreCase = true)
        lock.withLock {
            getInternal1(cookies, domainIndex, uri.host, secureLink)
            getInternal2(cookies, uriIndex, getEffectiveURI(uri), secureLink)
        }
        return cookies
    }

    override fun getCookies(): List<HttpCookie> = lock.withLock {
        cookieJar.removeIf(HttpCookie::hasExpired)
        cookieJar.toList()
    }

    override fun getURIs(): List<URI> = lock.withLock {
        uriIndex.entries.removeIf { it.value.isEmpty() }
        uriIndex.keys.toList()
    }

    override fun remove(uri: URI?, cookie: HttpCookie?): Boolean {
        requireNotNull(cookie) { "cookie is null" }

        return lock.withLock {
            cookieJar.remove(cookie)
        }
    }

    override fun removeAll(): Boolean {
        return cookieJar.isNotEmpty() && lock.withLock {
            cookieJar.clear()
            domainIndex.clear()
            uriIndex.clear()
            true
        }
    }

    private fun netscapeDomainMatches(domain: String?, host: String?): Boolean {
        if (domain == null || host == null) return false

        val isLocalDomain = ".local".equals(domain, ignoreCase = true)
        val embeddedDotInDomain = domain.indexOf('.', if (domain.startsWith(".")) 1 else 0)
        if (!isLocalDomain && (embeddedDotInDomain == -1 || embeddedDotInDomain == domain.length - 1)) {
            return false
        }

        val firstDotInHost = host.indexOf('.')
        if (firstDotInHost == -1 && isLocalDomain) return true

        val lengthDiff = host.length - domain.length
        return when {
            lengthDiff == 0 -> host.equals(domain, ignoreCase = true)
            lengthDiff > 0 -> host.substring(lengthDiff).equals(domain, ignoreCase = true)
            lengthDiff == -1 -> domain.startsWith(".") && host.equals(domain.substring(1), ignoreCase = true)
            else -> false
        }
    }

    private fun getInternal1(
        cookies: MutableList<HttpCookie>,
        cookieIndex: Map<String, MutableList<HttpCookie>>,
        host: String,
        secureLink: Boolean
    ) {
        val toRemove = mutableListOf<HttpCookie>()
        for ((domain, lst) in cookieIndex) {
            for (c in lst) {
                if ((c.version == 0 && netscapeDomainMatches(
                        domain,
                        host
                    )) || (c.version == 1 && HttpCookie.domainMatches(domain, host))
                ) {
                    if (cookieJar.contains(c)) {
                        if (!c.hasExpired() && (secureLink || !c.secure) && !cookies.contains(c)) {
                            cookies.add(c)
                        } else {
                            toRemove.add(c)
                        }
                    } else {
                        toRemove.add(c)
                    }
                }
            }
            lst.removeAll(toRemove)
            cookieJar.removeAll(toRemove)
            toRemove.clear()
        }
    }

    private fun <T> getInternal2(
        cookies: MutableList<HttpCookie>,
        cookieIndex: Map<T, MutableList<HttpCookie>>,
        comparator: Comparable<T>,
        secureLink: Boolean
    ) {
        for ((index, indexedCookies) in cookieIndex) {
            if (comparator.compareTo(index) == 0) {
                val it = indexedCookies.iterator()
                while (it.hasNext()) {
                    val ck = it.next()
                    if (cookieJar.contains(ck)) {
                        if (!ck.hasExpired() && (secureLink || !ck.secure) && !cookies.contains(ck)) {
                            cookies.add(ck)
                        } else {
                            it.remove()
                            cookieJar.remove(ck)
                        }
                    } else {
                        it.remove()
                    }
                }
            }
        }
    }

    private fun <T> addIndex(indexStore: MutableMap<T, MutableList<HttpCookie>>, index: T, cookie: HttpCookie) {
        indexStore.computeIfAbsent(index) { mutableListOf() }.apply {
            remove(cookie)
            add(cookie)
        }
    }

    private fun getEffectiveURI(uri: URI): URI {
        return try {
            URI("http", uri.host, null, null, null)
        } catch (ignored: URISyntaxException) {
            uri
        }
    }
}
