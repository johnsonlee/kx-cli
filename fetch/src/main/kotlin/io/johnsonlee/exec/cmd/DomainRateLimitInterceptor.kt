package io.johnsonlee.exec.cmd

import java.util.concurrent.ConcurrentHashMap
import okhttp3.Interceptor
import okhttp3.Response

/**
 * An interceptor that limits the number of requests to a specific domain per minute.
 *
 * @param defaultLimitPerMinute The default limit of requests per minute for each domain.
 */
class DomainRateLimitInterceptor(private val defaultLimitPerMinute: Int) : Interceptor {

    private val limiters = ConcurrentHashMap<String, RateLimiter>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        val rootDomain = extractRootDomain(host)
        val limiter = limiters.computeIfAbsent(rootDomain) {
            RateLimiter(defaultLimitPerMinute)
        }

        limiter.acquire()
        return chain.proceed(request)
    }

    private fun extractRootDomain(host: String): String {
        val parts = host.split(".")
        return if (parts.size >= 2) {
            parts.takeLast(2).joinToString(".")
        } else {
            host
        }
    }
}