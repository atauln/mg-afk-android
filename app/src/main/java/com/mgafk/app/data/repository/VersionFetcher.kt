package com.mgafk.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

object VersionFetcher {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchGameVersion(host: String = "magicgarden.gg"): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://$host/platform/v1/version")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty version response")
        val obj = json.parseToJsonElement(body).jsonObject
        obj["version"]?.jsonPrimitive?.content ?: throw Exception("No version field")
    }
}
