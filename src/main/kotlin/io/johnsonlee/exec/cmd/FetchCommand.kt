package io.johnsonlee.exec.cmd

import com.google.auto.service.AutoService
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import picocli.CommandLine
import java.io.File

@AutoService(Command::class)
open class FetchCommand : IOCommand() {

    @CommandLine.Option(names = ["-u", "--username"], description = ["Username"])
    lateinit var username: String

    @CommandLine.Option(names = ["-p", "--password"], description = ["Password"])
    lateinit var password: String

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
        OkHttpClient.Builder().authenticator(authenticator).build()
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
