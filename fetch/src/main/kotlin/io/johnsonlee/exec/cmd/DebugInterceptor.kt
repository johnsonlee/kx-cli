package io.johnsonlee.exec.cmd

import okhttp3.Interceptor
import okhttp3.Response

class DebugInterceptor(private val debuggable: () -> Boolean) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return try {
            chain.proceed(request)
        } finally {
            if (debuggable()) {
                System.err.println("✨ ${request.method.uppercase()} ${request.url}")
            }
        }
    }

}