package com.mgafk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mgafk.app.data.model.AbilityLog
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.Pet
import com.mgafk.app.data.model.ReconnectConfig
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.ShopState
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.SessionRepository
import com.mgafk.app.data.repository.VersionFetcher
import com.mgafk.app.data.websocket.ClientEvent
import com.mgafk.app.data.websocket.RoomClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val sessions: List<Session> = listOf(Session()),
    val activeSessionId: String = "",
    val alerts: AlertConfig = AlertConfig(),
    val connecting: Boolean = false,
    val apiReady: Boolean = false,
) {
    val activeSession: Session
        get() = sessions.find { it.id == activeSessionId } ?: sessions.first()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SessionRepository(application)
    private val clients = mutableMapOf<String, RoomClient>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sessions = repo.loadSessions().ifEmpty { listOf(Session()) }
            val activeId = repo.loadActiveSessionId() ?: sessions.first().id
            val alerts = repo.loadAlerts()
            _state.value = UiState(
                sessions = sessions,
                activeSessionId = activeId,
                alerts = alerts,
            )
            // Preload ALL API data in parallel at startup
            launch {
                MgApi.preloadAll()
                _state.update { it.copy(apiReady = true) }
            }
        }
    }

    // ---- Session Management ----

    fun addSession() {
        _state.update { s ->
            val newSession = Session(name = "Session ${s.sessions.size + 1}")
            s.copy(sessions = s.sessions + newSession, activeSessionId = newSession.id)
        }
        persist()
    }

    fun removeSession(id: String) {
        val client = clients.remove(id)
        client?.dispose()
        _state.update { s ->
            val filtered = s.sessions.filter { it.id != id }
            val sessions = filtered.ifEmpty { listOf(Session()) }
            val activeId = if (s.activeSessionId == id) sessions.first().id else s.activeSessionId
            s.copy(sessions = sessions, activeSessionId = activeId)
        }
        persist()
    }

    fun selectSession(id: String) {
        _state.update { it.copy(activeSessionId = id) }
        viewModelScope.launch { repo.saveActiveSessionId(id) }
    }

    fun updateSession(id: String, transform: (Session) -> Session) {
        _state.update { s ->
            s.copy(sessions = s.sessions.map { if (it.id == id) transform(it) else it })
        }
        persist()
    }

    // ---- Connection ----

    fun connect(sessionId: String) {
        val session = _state.value.sessions.find { it.id == sessionId } ?: return
        if (session.cookie.isBlank()) return

        updateSession(sessionId) { it.copy(busy = true, status = SessionStatus.CONNECTING) }

        viewModelScope.launch {
            try {
                val version = VersionFetcher.fetchGameVersion(
                    host = session.gameUrl.removePrefix("https://").removePrefix("http://").ifBlank { "magicgarden.gg" }
                )

                val client = clients.getOrPut(sessionId) { RoomClient() }

                // Listen for events
                launch {
                    client.events.collect { event ->
                        handleClientEvent(sessionId, event)
                    }
                }

                val host = session.gameUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .ifBlank { "magicgarden.gg" }

                client.connect(
                    version = version,
                    cookie = session.cookie,
                    room = session.room,
                    host = host,
                    reconnect = session.reconnect,
                )
            } catch (e: Exception) {
                updateSession(sessionId) {
                    it.copy(
                        busy = false,
                        status = SessionStatus.ERROR,
                        error = e.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    fun disconnect(sessionId: String) {
        clients[sessionId]?.disconnect()
        updateSession(sessionId) {
            it.copy(
                connected = false,
                busy = false,
                status = SessionStatus.IDLE,
                players = 0,
                uptime = "00:00:00",
            )
        }
    }

    fun setToken(sessionId: String, token: String) {
        updateSession(sessionId) { it.copy(cookie = token) }
    }

    fun clearToken(sessionId: String) {
        updateSession(sessionId) { it.copy(cookie = "") }
    }

    // ---- Alerts ----

    fun updateAlerts(transform: (AlertConfig) -> AlertConfig) {
        _state.update { it.copy(alerts = transform(it.alerts)) }
        viewModelScope.launch { repo.saveAlerts(_state.value.alerts) }
    }

    // ---- Internal ----

    private fun handleClientEvent(sessionId: String, event: ClientEvent) {
        when (event) {
            is ClientEvent.StatusChanged -> {
                updateSession(sessionId) {
                    it.copy(
                        status = event.status,
                        connected = event.status == SessionStatus.CONNECTED,
                        busy = event.status == SessionStatus.CONNECTING,
                        error = event.message,
                        playerId = event.playerId.ifBlank { it.playerId },
                        room = event.room.ifBlank { it.room },
                    )
                }
            }
            is ClientEvent.PlayersChanged -> {
                updateSession(sessionId) { it.copy(players = event.count) }
            }
            is ClientEvent.UptimeChanged -> {
                updateSession(sessionId) { it.copy(uptime = event.text) }
            }
            is ClientEvent.AbilityLogged -> {
                updateSession(sessionId) {
                    it.copy(logs = (listOf(event.log) + it.logs).take(200))
                }
            }
            is ClientEvent.LiveStatusChanged -> {
                updateSession(sessionId) {
                    it.copy(
                        playerName = event.playerName,
                        roomId = event.roomId,
                        weather = event.weather,
                        pets = event.pets,
                    )
                }
            }
            is ClientEvent.ShopsChanged -> {
                updateSession(sessionId) { it.copy(shops = event.shops) }
            }
            is ClientEvent.DebugLog -> { /* Could be stored for dev tools */ }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            repo.saveSessions(_state.value.sessions)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clients.values.forEach { it.dispose() }
        clients.clear()
    }
}
