package io.johnsonlee.exec.internal.network

import com.fasterxml.jackson.annotation.JsonProperty

abstract class HttpCookieMixin {

    @get:JsonProperty("name")
    abstract val name: String

    @get:JsonProperty("value")
    abstract val value: String

    @get:JsonProperty("domain")
    abstract val domain: String?

    @get:JsonProperty("path")
    abstract val path: String?

    @get:JsonProperty("maxAge")
    abstract val maxAge: Long

    @get:JsonProperty("secure")
    abstract val secure: Boolean

    @get:JsonProperty("httpOnly")
    abstract val httpOnly: Boolean

    @get:JsonProperty("expires")
    abstract val expires: Long
}