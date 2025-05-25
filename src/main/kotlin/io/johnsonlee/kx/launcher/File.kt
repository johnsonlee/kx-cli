package io.johnsonlee.kx.launcher

import java.io.File
import java.security.MessageDigest

fun File.md5AndSha1(): Pair<String, String> {
    val md5 = MessageDigest.getInstance("MD5")
    val sha1= MessageDigest.getInstance("SHA-1")

    this.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var readBytes: Int
        while (input.read(buffer).also { readBytes = it } != -1) {
            md5.update(buffer, 0, readBytes)
            sha1.update(buffer, 0, readBytes)
        }
    }

    return md5.digest().joinToString("") { "%02x".format(it) } to sha1.digest().joinToString("") { "%02x".format(it) }
}