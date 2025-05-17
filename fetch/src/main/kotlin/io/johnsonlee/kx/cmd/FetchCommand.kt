package io.johnsonlee.kx.cmd

import com.google.auto.service.AutoService
import io.johnsonlee.kx.internal.network.toCookieStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URL
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import picocli.CommandLine

@AutoService(Command::class)
open class FetchCommand : IOCommand() {

    @CommandLine.Option(names = ["-u", "--username"], description = ["Username"])
    lateinit var username: String

    @CommandLine.Option(names = ["-p", "--password"], description = ["Password"])
    lateinit var password: String

    @CommandLine.Option(names = ["--cookie-jar"], description = ["Cookie jar file"])
    lateinit var cookieJar: File

    @CommandLine.Option(names = ["--max-request-per-minute"], description = ["Max request per minute"], defaultValue = "30")
    var maxRequestPerMinute: Int = 30

    private val authenticator: Authenticator = Authenticator { _, response ->
        if (::username.isInitialized && ::password.isInitialized) {
            response.request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        } else {
            response.request
        }
    }

    private val client by lazy {
        val cookieStore = if (::cookieJar.isInitialized && cookieJar.exists()) {
            Json.parseToJsonElement(cookieJar.readText()).jsonArray.mapNotNull { json ->
                val cookie = json.jsonObject
                val name = cookie["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val value = cookie["value"]?.jsonPrimitive?.content ?: return@mapNotNull null

                HttpCookie(name, value).apply {
                    cookie["domain"]?.jsonPrimitive?.content?.let {
                        this.domain = it
                    }
                    cookie["path"]?.jsonPrimitive?.content?.let {
                        this.path = it
                    }
                    cookie["maxAge"]?.jsonPrimitive?.longOrNull?.let {
                        this.maxAge = it
                    }
                    cookie["secure"]?.jsonPrimitive?.booleanOrNull?.let {
                        this.secure = it
                    }
                    cookie["httpOnly"]?.jsonPrimitive?.booleanOrNull?.let {
                        this.isHttpOnly = it
                    }
                    cookie["version"]?.jsonPrimitive?.longOrNull?.toInt()?.let {
                        this.version = it
                    }
                }
            }.toCookieStore()
        } else {
            null
        }
        OkHttpClient.Builder()
            .authenticator(authenticator)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    header("accept-language", "en-US,en")
                    header("cache-control", "no-cache")
                    header("pragma", "no-cache")
                    header("priority", "u=0, i")
                    header("sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"")
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
            .addInterceptor(DomainRateLimitInterceptor(maxRequestPerMinute))
            .addNetworkInterceptor(VerboseInterceptor(::verbose))
            .cookieJar(JavaNetCookieJar(CookieManager(cookieStore, null)))
            .build()
    }

    override fun run(): Unit = runBlocking {
        File(output).outputStream().use { dest ->
            input.asFlow().map { url ->
                get(url) { src ->
                    src.copyTo(dest)
                }
            }.collect()
        }
    }

    protected fun <T> get(uri: String, transform: (InputStream) -> T): T = try {
        val url = uri.toHttpUrl()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            response.takeIf(Response::isSuccessful)?.body?.use { body ->
                body.byteStream().use {
                    transform(it)
                }
            } ?: error("Loading document from $uri failed: ${response.code}")
        }
    } catch (e: IllegalArgumentException) {
        if (uri == "--" || uri == "-") {
            transform(System.`in`)
        } else {
            try {
                URL(uri).openStream().use {
                    transform(it)
                }
            } catch (e: IOException) {
                File(uri).takeIf(File::exists)?.inputStream()?.use {
                    transform(it)
                } ?: error("Loading document from $uri failed: ${e.message}")
            }
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(FetchCommand()).execute(*args))
}
