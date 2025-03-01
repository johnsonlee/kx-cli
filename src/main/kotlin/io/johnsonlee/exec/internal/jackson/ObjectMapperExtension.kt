package io.johnsonlee.exec.internal.jackson

import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.addMixIn
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.johnsonlee.exec.internal.network.HttpCookieMixin
import java.net.HttpCookie

internal val objectMapper = jacksonObjectMapper().apply {
    registerKotlinModule()
    registerModules(JavaTimeModule())
    addMixIn<HttpCookie, HttpCookieMixin>()
    disable(WRITE_DATES_AS_TIMESTAMPS)
}