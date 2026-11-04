package com.omnilink.remote.model

import com.google.gson.annotations.SerializedName

// Signaling Message Types
sealed class SignalingMessage {
    data class RegisterHost(
        val type: String = "register-host",
        val id: String,
        val pin: String,
        val deviceName: String,
        val unattendedPassword: String? = null,
        val allowRemoteControl: Boolean = true,
        val allowFileTransfer: Boolean = true
    )

    data class ConnectRequest(
        val type: String = "connect-request",
        val targetId: String,
        val pin: String? = null,
        val password: String? = null,
        val clientName: String = "OmniLink Mobile Controller"
    )

    data class SdpOffer(
        val type: String = "offer",
        val targetId: String,
        val sdp: SdpPayload
    )

    data class SdpAnswer(
        val type: String = "answer",
        val targetId: String,
        val sdp: SdpPayload
    )

    data class IceCandidate(
        val type: String = "ice-candidate",
        val targetId: String,
        val candidate: IcePayload
    )
}

data class SdpPayload(
    val type: String,
    val description: String
)

data class IcePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
)

// Remote Input Packets (Sent over WebRTC DataChannel)
data class RemoteInputPacket(
    @SerializedName("t") val type: String = "input",
    @SerializedName("a") val action: String, // "DOWN", "MOVE", "UP", "CLICK", "SCROLL_UP", "SCROLL_DOWN"
    @SerializedName("x") val x: Float,       // 0.0f to 1.0f (Screen percentage)
    @SerializedName("y") val y: Float,       // 0.0f to 1.0f (Screen percentage)
    @SerializedName("ts") val timestamp: Long = System.currentTimeMillis()
)

data class RemoteNavCommand(
    @SerializedName("t") val type: String = "nav",
    @SerializedName("cmd") val command: String // "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "LOCK"
)

// File Transfer Metadata
data class FileTransferMeta(
    val type: String = "file-meta",
    val name: String,
    val size: Long,
    val mime: String
)

// Session State Information
data class SessionState(
    val myId: String = "",
    val myPin: String = "",
    val isSharingActive: Boolean = false,
    val connectedControllerName: String? = null,
    val isAccessibilityEnabled: Boolean = false,
    val unattendedPassword: String = "",
    val isUnattendedEnabled: Boolean = false,
    val serverUrl: String = "http://192.168.1.100:3000" // Customizable server IP/Domain
)
