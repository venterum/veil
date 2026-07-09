package com.v2ray.ang.dto

data class UrlContentRequest(
    val url: String?,
    val timeout: Int = 15000,
    val httpPort: Int = 0,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val userAgent: String? = null
)

data class HttpResponseWithHeaders(
    val body: String = "",
    val headers: Map<String, String> = emptyMap()
)
