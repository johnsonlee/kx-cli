package io.johnsonlee.kx.cmd

import okhttp3.Interceptor
import okhttp3.Response

class VerboseInterceptor(private val verbose: (String) -> Unit) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return try {
            chain.proceed(request)
        } finally {
            verbose("✨ ${request.method.uppercase()} ${request.url}")
        }
    }

}