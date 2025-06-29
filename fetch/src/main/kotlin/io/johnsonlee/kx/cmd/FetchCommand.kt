package io.johnsonlee.kx.cmd

import com.google.auto.service.AutoService
import io.johnsonlee.ktx.okhttp.okhttpClient
import io.johnsonlee.ktx.okhttp.toCookieStore
import java.io.File
import java.io.InputStream
import java.net.CookieManager
import java.net.HttpCookie
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
import okhttp3.MediaType
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

    @CommandLine.Option(
        names = ["--max-request-per-minute"],
        description = ["Max request per minute"],
        defaultValue = "30"
    )
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
        okhttpClient(useDaemonThread = true).newBuilder()
            .authenticator(authenticator)
            .addNetworkInterceptor(VerboseInterceptor(::verbose))
            .cookieJar(JavaNetCookieJar(CookieManager(cookieStore, null)))
            .build()
    }

    override fun run(): Unit = runBlocking {
        File(output).outputStream().use { dest ->
            input.asFlow().map { url ->
                get(url) { _, src ->
                    src.copyTo(dest)
                }
            }.collect()
        }
    }

    protected fun <T> get(uri: String, transform: (MediaType?, InputStream) -> T): T = try {
        val url = uri.toHttpUrl()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            response.takeIf(Response::isSuccessful)?.body?.use { body ->
                body.byteStream().buffered().use {
                    transform(body.contentType(), it)
                }
            } ?: error("Loading document from $uri failed: ${response.code}")
        }
    } catch (e: IllegalArgumentException) {
        if (uri == "--" || uri == "-") {
            transform(null, System.`in`.buffered())
        } else {
            File(uri).takeIf(File::exists)?.inputStream()?.buffered()?.use {
                transform(null, it)
            } ?: error("Loading document from $uri failed: ${e.message}")
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(FetchCommand()).execute(*args))
}
