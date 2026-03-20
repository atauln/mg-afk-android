package com.mgafk.app.data.websocket

import com.mgafk.app.data.model.AbilityLog
import com.mgafk.app.data.model.Pet
import com.mgafk.app.data.model.ReconnectConfig
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.ShopState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

sealed class ClientEvent {
    data class StatusChanged(
        val status: SessionStatus,
        val message: String = "",
        val code: Int? = null,
        val room: String = "",
        val playerId: String = "",
        val retry: Int = 0,
        val maxRetries: Int = 0,
        val retryInMs: Long = 0,
    ) : ClientEvent()

    data class PlayersChanged(val count: Int) : ClientEvent()
    data class UptimeChanged(val text: String) : ClientEvent()
    data class AbilityLogged(val log: AbilityLog) : ClientEvent()
    data class LiveStatusChanged(
        val playerId: String,
        val playerName: String,
        val roomId: String,
        val weather: String,
        val pets: List<Pet>,
    ) : ClientEvent()

    data class ShopsChanged(val shops: ShopState) : ClientEvent()
    data class DebugLog(val level: String, val message: String, val detail: String = "") : ClientEvent()
}

class RoomClient {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var socketToken = 0
    private var state = "idle"

    // Connection state
    private var host = Constants.DEFAULT_HOST
    private var version = Constants.DEFAULT_VERSION
    var room = ""
        private set
    var playerId = ""
        private set
    private var cookie = ""
    private var userAgent = Constants.DEFAULT_UA

    // Game state
    private var roomState: JsonElement? = null
    private var gameState: JsonElement? = null
    private var playerIndex = -1
    private var userSlotIndex: Int? = null
    private var welcomed = false
    private var connectedAt = 0L
    private var manualClose = false
    private var playerCount = 0
    private var lastLiveKey = ""
    private var lastShopsKey = ""

    // Retry state
    private var retryCount = 0
    private var retryCode: Int? = null
    private var retryJob: Job? = null
    private var hasEverWelcomed = false
    private var initialConnectFastRetry = false
    var reconnectConfig = ReconnectConfig()
        private set

    // Keepalive jobs
    private var textPingJob: Job? = null
    private var appPingJob: Job? = null
    private var tickerJob: Job? = null

    // Last connect options for retry
    private var lastConnectOpts: ConnectOptions? = null

    private val _events = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ClientEvent> = _events.asSharedFlow()

    data class ConnectOptions(
        val version: String,
        val room: String,
        val cookie: String,
        val host: String,
        val userAgent: String,
    )

    fun connect(
        version: String,
        cookie: String,
        room: String = "",
        host: String = Constants.DEFAULT_HOST,
        userAgent: String = Constants.DEFAULT_UA,
        reconnect: ReconnectConfig? = null,
        isRetry: Boolean = false,
    ): String {
        val nextCookie = IdGenerator.normalizeCookie(cookie)
        val nextRoom = room.trim().ifEmpty { IdGenerator.generateRoomId() }

        if (nextCookie.isEmpty() || version.isBlank()) {
            throw IllegalArgumentException("Missing version or cookie")
        }

        webSocket?.let { disconnect() }

        if (!isRetry) {
            retryCount = 0
            retryCode = null
            initialConnectFastRetry = !hasEverWelcomed
        }
        cancelRetryJob()

        if (reconnect != null) this.reconnectConfig = reconnect

        this.host = host
        this.version = version.trim()
        this.room = nextRoom
        this.cookie = nextCookie
        this.userAgent = userAgent
        this.playerId = IdGenerator.generatePlayerId()
        this.roomState = null
        this.gameState = null
        this.playerCount = 0
        this.connectedAt = 0
        this.welcomed = false
        this.manualClose = false
        this.playerIndex = -1
        this.userSlotIndex = null
        this.lastLiveKey = ""
        this.lastShopsKey = ""

        lastConnectOpts = ConnectOptions(
            version = this.version,
            room = this.room,
            cookie = this.cookie,
            host = this.host,
            userAgent = this.userAgent,
        )

        val url = UrlBuilder.buildUrl(this.host, this.version, this.room, this.playerId)

        state = "connecting"
        emitStatus(SessionStatus.CONNECTING)

        val token = ++socketToken
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", this.userAgent)
            .header("Cookie", this.cookie)
            .header("Origin", "https://${this.host}")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token != socketToken) return
                handleOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (token != socketToken) return
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (token != socketToken) return
                handleClose(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (token != socketToken) return
                handleError(t)
            }
        })

        return url
    }

    fun disconnect() {
        clearTimers()
        state = "disconnected"
        connectedAt = 0
        welcomed = false
        roomState = null
        playerCount = 0
        manualClose = true
        cancelRetryJob()
        retryCount = 0
        retryCode = null
        initialConnectFastRetry = false
        emitStatus(SessionStatus.IDLE, code = 1000)

        val ws = webSocket
        webSocket = null
        socketToken++
        ws?.close(1000, "client disconnect")
    }

    fun dispose() {
        disconnect()
        scope.cancel()
    }

    // ---- Internal handlers ----

    private fun handleOpen() {
        send("""{"scopePath":["Room"],"type":"VoteForGame","gameName":"${Constants.GAME_NAME}"}""")
        send("""{"scopePath":["Room"],"type":"SetSelectedGame","gameName":"${Constants.GAME_NAME}"}""")

        textPingJob = scope.launch {
            while (isActive) {
                delay(Constants.TEXT_PING_MS)
                send("ping")
            }
        }

        appPingJob = scope.launch {
            while (isActive) {
                delay(Constants.APP_PING_MS)
                send("""{"scopePath":["Room","${Constants.GAME_NAME}"],"type":"Ping","id":${System.currentTimeMillis()}}""")
            }
        }
    }

    private fun handleMessage(raw: String) {
        if (raw == "ping" || raw == "\"ping\"") {
            send("pong")
            return
        }

        val msg: JsonObject = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return
        }

        val type = msg["type"]?.jsonPrimitive?.contentOrNull

        if (type == "Welcome") {
            handleWelcome(msg)
            return
        }

        if (type == "PartialState") {
            handlePartialState(msg)
        }
    }

    private fun handleWelcome(msg: JsonObject) {
        val fullState = msg["fullState"]?.jsonObject ?: return
        val roomData = fullState["data"]
        val gameData = fullState["child"]?.jsonObject?.get("data")

        // Check auth
        val players = StateParser.extractPlayers(roomData)
        val me = players.find { it.id == playerId }
        if (me != null && me.databaseUserId == null) {
            failAuth("Invalid mc_jwt cookie.")
            return
        }

        roomState = roomData
        gameState = gameData
        emitPlayerCount()
        emitLiveStatus()
        emitShops()

        if (!welcomed) {
            welcomed = true
            connectedAt = System.currentTimeMillis()
            state = "connected"
            retryCount = 0
            retryCode = null
            hasEverWelcomed = true
            initialConnectFastRetry = false
            cancelRetryJob()
            emitStatus(SessionStatus.CONNECTED, room = room, playerId = playerId)
            startTicker()
        }
    }

    private fun handlePartialState(msg: JsonObject) {
        val patches = msg["patches"] as? JsonArray ?: return
        var liveDirty = false
        var shopsDirty = false

        for (patchEl in patches) {
            val patch = patchEl as? JsonObject ?: continue
            val path = patch["path"]?.jsonPrimitive?.contentOrNull ?: continue
            val value = patch["value"]
            val op = patch["op"]?.jsonPrimitive?.contentOrNull

            // Room state patches
            if (roomState != null && (
                path.matches(Regex("^/data/players/\\d+(/.*)?$")) ||
                path.matches(Regex("^/data/(roomId|roomSessionId|hostPlayerId)(/.*)?$"))
            )) {
                roomState = JsonPatch.applyPatch(roomState!!, path, value, op)
                liveDirty = true
                continue
            }

            // Game state patches
            if (gameState != null) {
                val gamePath = path.removePrefix("/child")

                if (path == "/child/data/weather") {
                    gameState = JsonPatch.applyPatch(gameState!!, gamePath, value, op)
                    liveDirty = true
                    continue
                }

                if (path.matches(Regex("^/child/data/userSlots/.*$"))) {
                    gameState = JsonPatch.applyPatch(gameState!!, gamePath, value, op)
                    liveDirty = true
                    continue
                }

                if (path.startsWith("/child/data/shops")) {
                    gameState = JsonPatch.applyPatch(gameState!!, gamePath, value, op)
                    shopsDirty = true
                    continue
                }
            }
        }

        // Re-find slot index
        if (roomState != null && gameState != null) {
            val players = StateParser.extractPlayers(roomState)
            val idx = players.indexOfFirst { it.id == playerId }
            if (idx >= 0) playerIndex = idx
            val si = StateParser.findUserSlotIndex(roomState, gameState, playerId, playerIndex)
            if (si != null) userSlotIndex = si
        }

        emitPlayerCount()
        if (liveDirty) emitLiveStatus()
        if (shopsDirty) emitShops()

        // Extract ability logs from patches
        extractAbilityLogFromPatches(patches)
    }

    private fun extractAbilityLogFromPatches(patches: JsonArray) {
        val logRegex = Regex(
            "/child/data/userSlots/(\\d+)/data/activityLogs/(\\d+)/(action|timestamp)"
        )
        val petNameRegex = Regex(
            "/child/data/userSlots/(\\d+)/data/activityLogs/(\\d+)/parameters/pet/name"
        )
        val petSpeciesRegex = Regex(
            "/child/data/userSlots/(\\d+)/data/activityLogs/(\\d+)/parameters/pet/petSpecies"
        )

        data class LogEntry(
            var action: String? = null,
            var timestamp: Long? = null,
            var petName: String? = null,
            var petSpecies: String? = null,
            var slotIndex: Int? = null,
            var seen: Int = 0,
        )

        val logs = mutableMapOf<Int, LogEntry>()
        var seen = 0

        for (patchEl in patches) {
            val patch = patchEl as? JsonObject ?: continue
            val path = patch["path"]?.jsonPrimitive?.contentOrNull ?: continue
            val value = patch["value"]

            logRegex.find(path)?.let { match ->
                val slotIdx = match.groupValues[1].toInt()
                val logIdx = match.groupValues[2].toInt()
                val field = match.groupValues[3]
                if (userSlotIndex != null && slotIdx != userSlotIndex) return@let
                val entry = logs.getOrPut(logIdx) { LogEntry() }
                entry.slotIndex = slotIdx
                entry.seen = ++seen
                when (field) {
                    "action" -> entry.action = value?.jsonPrimitive?.contentOrNull
                    "timestamp" -> entry.timestamp = value?.jsonPrimitive?.longOrNull
                }
                return@let
            }

            petNameRegex.find(path)?.let { match ->
                val slotIdx = match.groupValues[1].toInt()
                val logIdx = match.groupValues[2].toInt()
                if (userSlotIndex != null && slotIdx != userSlotIndex) return@let
                val entry = logs.getOrPut(logIdx) { LogEntry() }
                entry.petName = value?.jsonPrimitive?.contentOrNull
                entry.slotIndex = slotIdx
                entry.seen = ++seen
            }

            petSpeciesRegex.find(path)?.let { match ->
                val slotIdx = match.groupValues[1].toInt()
                val logIdx = match.groupValues[2].toInt()
                if (userSlotIndex != null && slotIdx != userSlotIndex) return@let
                val entry = logs.getOrPut(logIdx) { LogEntry() }
                entry.petSpecies = value?.jsonPrimitive?.contentOrNull
                entry.slotIndex = slotIdx
                entry.seen = ++seen
            }
        }

        // Find best log entry (highest timestamp, then highest index)
        val best = logs.entries
            .filter { StateParser.isAbilityName(it.value.action) }
            .maxWithOrNull(compareBy({ it.value.timestamp ?: 0L }, { it.value.seen }))

        if (best != null) {
            val entry = best.value
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)
            _events.tryEmit(
                ClientEvent.AbilityLogged(
                    AbilityLog(
                        timestamp = System.currentTimeMillis(),
                        action = entry.action.orEmpty(),
                        petName = entry.petName.orEmpty(),
                        petSpecies = entry.petSpecies.orEmpty(),
                        slotIndex = entry.slotIndex ?: userSlotIndex ?: 0,
                    )
                )
            )
        }
    }

    private fun handleClose(code: Int, reason: String) {
        clearTimers()
        state = "disconnected"
        connectedAt = 0
        welcomed = false
        webSocket = null

        emit(ClientEvent.DebugLog("info", "ws closed", "code=$code reason=$reason"))

        if (!manualClose) {
            if (shouldReconnect(code)) {
                if (scheduleReconnect(code, reason)) return
            }
            emitStatus(SessionStatus.IDLE, code = code)
            return
        }
        emitStatus(SessionStatus.IDLE, code = code)
    }

    private fun handleError(throwable: Throwable) {
        val msg = throwable.message ?: throwable.toString()
        emit(ClientEvent.DebugLog("error", "ws error", msg))
        // OkHttp calls onFailure instead of onClosed on error — treat as close
        handleClose(1006, msg)
    }

    private fun failAuth(message: String) {
        clearTimers()
        cancelRetryJob()
        state = "error"
        connectedAt = 0
        welcomed = false
        roomState = null
        gameState = null
        playerCount = 0
        retryCount = 0
        retryCode = null
        initialConnectFastRetry = false
        emitStatus(SessionStatus.ERROR, message = message, code = 4800)
        val ws = webSocket
        webSocket = null
        socketToken++
        ws?.close(1000, "auth failed")
    }

    // ---- Reconnection ----

    private fun shouldReconnect(code: Int): Boolean {
        if (code !in Constants.KNOWN_CLOSE_CODES) return reconnectConfig.unknown
        return reconnectConfig.codes[code] ?: reconnectConfig.unknown
    }

    private fun getReconnectDelay(code: Int): Long {
        val configuredDelay = if (code in Constants.SUPERSEDED_CODES) {
            max(0, reconnectConfig.delays.supersededMs)
        } else {
            max(0, reconnectConfig.delays.otherMs)
        }
        val base = max(configuredDelay, Constants.RETRY_DELAY_MS)
        val backoff = min(
            base * 2.0.pow(max(0, retryCount - 1).toDouble()).toLong(),
            Constants.RETRY_MAX_DELAY_MS,
        )
        val jitter = (Math.random() * Constants.RETRY_JITTER_MS).toLong()
        return backoff + jitter
    }

    private fun getMaxRetries(code: Int): Int =
        if (code == 4800) Constants.AUTH_RETRY_MAX else Constants.RETRY_MAX

    private fun scheduleReconnect(code: Int, reason: String): Boolean {
        val isInitial = initialConnectFastRetry && !hasEverWelcomed
        val maxRetries = if (isInitial) 5 else getMaxRetries(code)
        if (!isInitial && !shouldReconnect(code)) return false
        if (lastConnectOpts == null) return false
        retryCode = code
        if (retryCount >= maxRetries) return false
        retryCount++
        val attempt = retryCount
        val delayMs = if (isInitial) 0L else getReconnectDelay(code)

        state = "connecting"
        emitStatus(
            SessionStatus.CONNECTING,
            message = "Reconnecting ($attempt/$maxRetries)...",
            code = code,
        )

        cancelRetryJob()
        retryJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val opts = lastConnectOpts ?: return@launch
            try {
                connect(
                    version = opts.version,
                    cookie = opts.cookie,
                    room = opts.room,
                    host = opts.host,
                    userAgent = opts.userAgent,
                    isRetry = true,
                )
            } catch (e: Exception) {
                emitStatus(SessionStatus.ERROR, message = e.message ?: e.toString())
            }
        }
        return true
    }

    private fun cancelRetryJob() {
        retryJob?.cancel()
        retryJob = null
    }

    // ---- Emitters ----

    private fun emit(event: ClientEvent) {
        _events.tryEmit(event)
    }

    private fun emitStatus(
        status: SessionStatus,
        message: String = "",
        code: Int? = null,
        room: String = "",
        playerId: String = "",
    ) {
        emit(ClientEvent.StatusChanged(status = status, message = message, code = code, room = room, playerId = playerId))
    }

    private fun emitPlayerCount() {
        val players = StateParser.extractPlayers(roomState)
        val count = players.count { it.isConnected }
        if (count != playerCount) {
            playerCount = count
            emit(ClientEvent.PlayersChanged(count))
        }
    }

    private fun emitLiveStatus() {
        val players = StateParser.extractPlayers(roomState)
        val idx = players.indexOfFirst { it.id == playerId }
        if (idx >= 0) playerIndex = idx
        val si = StateParser.findUserSlotIndex(roomState, gameState, playerId, playerIndex)
        if (si != null) userSlotIndex = si

        val me = players.find { it.id == playerId }
        val weather = (gameState as? JsonObject)?.get("weather")?.jsonPrimitive?.contentOrNull
        val pets = StateParser.extractPets(gameState, userSlotIndex)
        val roomId = (roomState as? JsonObject)?.get("roomId")?.jsonPrimitive?.contentOrNull.orEmpty()

        val payload = ClientEvent.LiveStatusChanged(
            playerId = playerId,
            playerName = me?.name.orEmpty(),
            roomId = roomId,
            weather = StateParser.formatWeather(weather),
            pets = pets,
        )
        val key = payload.toString()
        if (key == lastLiveKey) return
        lastLiveKey = key
        emit(payload)
    }

    private fun emitShops() {
        val shops = StateParser.extractShops(gameState)
        val key = shops.toString()
        if (key == lastShopsKey) return
        lastShopsKey = key
        emit(ClientEvent.ShopsChanged(shops))
    }

    // ---- Timers ----

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (connectedAt > 0) {
                    val ms = System.currentTimeMillis() - connectedAt
                    emit(ClientEvent.UptimeChanged(StateParser.fmtDuration(ms)))
                }
            }
        }
    }

    private fun clearTimers() {
        tickerJob?.cancel(); tickerJob = null
        textPingJob?.cancel(); textPingJob = null
        appPingJob?.cancel(); appPingJob = null
    }

    private fun send(text: String) {
        webSocket?.send(text)
    }
}
