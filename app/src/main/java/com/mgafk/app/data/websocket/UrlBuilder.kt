package com.mgafk.app.data.websocket

import android.net.Uri

object UrlBuilder {
    fun buildUrl(host: String, version: String, room: String, playerId: String): String {
        val base = "wss://$host/version/$version/api/rooms/$room/connect"
        return Uri.parse(base).buildUpon()
            .appendQueryParameter("surface", "\"web\"")
            .appendQueryParameter("platform", "\"desktop\"")
            .appendQueryParameter("playerId", "\"$playerId\"")
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("source", "\"manualUrl\"")
            .appendQueryParameter("capabilities", "\"fbo_mipmap_ok\"")
            .build()
            .toString()
    }
}
