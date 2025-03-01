package io.johnsonlee.exec.internal.network

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.HttpCookie

fun Collection<HttpCookie>.toJson(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(this)
}