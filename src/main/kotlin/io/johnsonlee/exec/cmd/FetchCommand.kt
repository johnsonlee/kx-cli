package io.johnsonlee.exec.cmd

import com.google.auto.service.AutoService
import okhttp3.*
import picocli.CommandLine
import java.io.File

@AutoService(Command::class)
class FetchCommand : IOCommand() {

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

    override fun run() {
        val okhttp = OkHttpClient.Builder().authenticator(authenticator).build()
        val request = Request.Builder().url(input).build()

        okhttp.newCall(request).execute().takeIf(Response::isSuccessful)?.use { response ->
            File(output).apply {
                parentFile?.mkdirs()
            }.outputStream().use { output ->
                response.body?.byteStream()?.use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

}
