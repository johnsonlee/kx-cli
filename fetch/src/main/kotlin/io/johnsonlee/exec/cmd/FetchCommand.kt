package io.johnsonlee.exec.cmd

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auto.service.AutoService
import io.johnsonlee.exec.internal.network.toCookieStore
import java.io.File
import java.net.CookieManager
import java.net.HttpCookie
import kotlin.system.exitProcess
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import picocli.CommandLine

@AutoService(Command::class)
open class FetchCommand : IOCommand() {

    companion object {
        private val objectMapper = jacksonObjectMapper().apply {
            registerKotlinModule()
            registerModules(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    @CommandLine.Option(names = ["-u", "--username"], description = ["Username"])
    lateinit var username: String

    @CommandLine.Option(names = ["-p", "--password"], description = ["Password"])
    lateinit var password: String

    @CommandLine.Option(names = ["--cookie-jar"], description = ["Cookie jar file"])
    lateinit var cookieJar: File

    private val authenticator: Authenticator = Authenticator { _, response ->
        if (::username.isInitialized && ::password.isInitialized) {
            response.request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        } else {
            response.request
        }
    }

    protected val client by lazy {
        val cookieStore = if (::cookieJar.isInitialized && cookieJar.exists()) {
            cookieJar.inputStream().use { input ->
                objectMapper.readValue<List<HttpCookie>>(input).toCookieStore()
            }
        } else {
            null
        }
        OkHttpClient.Builder()
            .authenticator(authenticator)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    header("accept-language", "zh-CN,zh;q=0.9,en;q=0.8")
                    header("cache-control", "no-cache")
                    header("pragma", "no-cache")
                    header("priority", "u=0, i")
                    header("sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
                    header("sec-ch-ua-mobile", "?0")
                    header("sec-ch-ua-platform", "\"macOS\"")
                    header("sec-fetch-dest", "document")
                    header("sec-fetch-mode", "navigate")
                    header("sec-fetch-site", "same-origin")
                    header("sec-fetch-user", "?1")
                    header("upgrade-insecure-requests", "1")
                    header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                }.build()
                chain.proceed(request)
            }
            .cookieJar(JavaNetCookieJar(CookieManager(cookieStore, null)))
            .build()
    }

    protected fun url(): HttpUrl = input.toHttpUrl()

    override fun run() {
        get(url()).takeIf(Response::isSuccessful)?.use { response ->
            File(output).apply {
                parentFile?.mkdirs()
            }.outputStream().use { output ->
                response.body?.byteStream()?.use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    protected fun get(url: HttpUrl): Response {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute()
    }

}

fun main(args: Array<String>) {
    exitProcess(CommandLine(FetchCommand()).execute(*args))
}
