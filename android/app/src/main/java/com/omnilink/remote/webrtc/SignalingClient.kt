package com.omnilink.remote.webrtc

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.omnilink.remote.model.IcePayload
import com.omnilink.remote.model.SdpPayload
import com.omnilink.remote.model.SignalingMessage
import okhttp3.*
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val serverBaseUrl: String,
    private val listener: SignalingListener
) {
    interface SignalingListener {
        fun onConnected()
        fun onRegistered(id: String, pin: String)
        fun onIncomingConnection(controllerId: String, controllerName: String)
        fun onOfferReceived(fromId: String, sdp: SdpPayload)
        fun onAnswerReceived(fromId: String, sdp: SdpPayload)
        fun onIceCandidateReceived(fromId: String, candidate: IcePayload)
        fun onRemoteInputReceived(action: String, x: Float, y: Float)
        fun onRemoteCommandReceived(command: String)
        fun onError(reason: String)
        fun onDisconnected()
    }

    companion object {
        private const val TAG = "OmniSignaling"
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for persistent stream
        .build()

    private var webSocket: WebSocket? = null
    private var isManuallyClosed = false

    fun connect() {
        isManuallyClosed = false
        val wsUrl = if (serverBaseUrl.startsWith("http://")) {
            serverBaseUrl.replace("http://", "ws://") + "/ws"
        } else if (serverBaseUrl.startsWith("https://")) {
            serverBaseUrl.replace("https://", "wss://") + "/ws"
        } else {
            "ws://$serverBaseUrl/ws"
        }

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Signaling Server: $wsUrl")
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JsonParser.parseString(text).asJsonObject
                    handleMessage(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: $text", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling WebSocket failure: ${t.message}")
                listener.onError(t.message ?: "Connection failed")
                if (!isManuallyClosed) {
                    // Auto-reconnect after 3 seconds
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isManuallyClosed) connect()
                    }, 3000)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Signaling WebSocket closed: $reason")
                listener.onDisconnected()
            }
        })
    }

    private fun handleMessage(json: JsonObject) {
        val type = json.get("type")?.asString ?: return
        when (type) {
            "registered" -> {
                val id = json.get("id").asString
                val pin = json.get("pin").asString
                listener.onRegistered(id, pin)
            }
            "incoming-connection" -> {
                val controllerId = json.get("controllerId").asString
                val controllerName = json.get("controllerName")?.asString ?: "Controller"
                listener.onIncomingConnection(controllerId, controllerName)
            }
            "offer" -> {
                val fromId = json.get("fromId").asString
                val sdpObj = json.getAsJsonObject("sdp")
                val sdp = SdpPayload(
                    type = sdpObj.get("type").asString,
                    description = sdpObj.get("sdp").asString
                )
                listener.onOfferReceived(fromId, sdp)
            }
            "answer" -> {
                val fromId = json.get("fromId").asString
                val sdpObj = json.getAsJsonObject("sdp")
                val sdp = SdpPayload(
                    type = sdpObj.get("type").asString,
                    description = sdpObj.get("sdp").asString
                )
                listener.onAnswerReceived(fromId, sdp)
            }
            "ice-candidate" -> {
                val fromId = json.get("fromId").asString
                val candObj = json.getAsJsonObject("candidate")
                val cand = IcePayload(
                    sdpMid = candObj.get("sdpMid")?.asString,
                    sdpMLineIndex = candObj.get("sdpMLineIndex")?.asInt ?: 0,
                    candidate = candObj.get("candidate").asString
                )
                listener.onIceCandidateReceived(fromId, cand)
            }
            "remote-input" -> {
                val action = json.get("a")?.asString ?: return
                val x = json.get("x")?.asFloat ?: 0f
                val y = json.get("y")?.asFloat ?: 0f
                listener.onRemoteInputReceived(action, x, y)
            }
            "remote-command" -> {
                val cmd = json.get("cmd")?.asString ?: return
                listener.onRemoteCommandReceived(cmd)
            }
            "connect-error" -> {
                val reason = json.get("reason")?.asString ?: "Unknown error"
                listener.onError(reason)
            }
        }
    }

    fun registerHost(id: String, pin: String, deviceName: String, unattendedPassword: String?) {
        val msg = SignalingMessage.RegisterHost(
            id = id,
            pin = pin,
            deviceName = deviceName,
            unattendedPassword = unattendedPassword
        )
        send(gson.toJson(msg))
    }

    fun sendAnswer(targetId: String, sdp: SdpPayload) {
        val msg = SignalingMessage.SdpAnswer(targetId = targetId, sdp = sdp)
        send(gson.toJson(msg))
    }

    fun sendIceCandidate(targetId: String, candidate: IcePayload) {
        val msg = SignalingMessage.IceCandidate(targetId = targetId, candidate = candidate)
        send(gson.toJson(msg))
    }

    private fun send(jsonText: String) {
        webSocket?.send(jsonText)
    }

    fun disconnect() {
        isManuallyClosed = true
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
}
