package com.omnilink.remote.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omnilink.remote.MainActivity
import com.omnilink.remote.R
import com.omnilink.remote.model.IcePayload
import com.omnilink.remote.model.SdpPayload
import com.omnilink.remote.webrtc.SignalingClient
import com.omnilink.remote.webrtc.WebRtcClient
import org.webrtc.PeerConnection

class ScreenCaptureService : Service(), WebRtcClient.WebRtcListener, SignalingClient.SignalingListener {

    companion object {
        private const val TAG = "OmniScreenService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "omni_screen_channel"

        const val ACTION_START = "ACTION_START_STREAM"
        const val ACTION_STOP = "ACTION_STOP_STREAM"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_PIN = "EXTRA_PIN"
        const val EXTRA_SERVER_URL = "EXTRA_SERVER_URL"
        const val EXTRA_ALLOW_CONTROL = "EXTRA_ALLOW_CONTROL"
    }

    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null

    private var currentSessionId: String = ""
    private var currentPin: String = ""
    private var serverUrl: String = ""
    private var allowControl: Boolean = true
    private var activeControllerId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val dataIntent = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                currentPin = intent.getStringExtra(EXTRA_PIN) ?: ""
                serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: "http://192.168.1.100:3000"
                allowControl = intent.getBooleanExtra(EXTRA_ALLOW_CONTROL, true)

                if (resultCode == Activity.RESULT_OK && dataIntent != null) {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification())
                    initWebRtcAndSignaling(resultCode, dataIntent)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopSharing()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun initWebRtcAndSignaling(resultCode: Int, data: Intent) {
        webRtcClient = WebRtcClient(applicationContext, this)
        webRtcClient?.startScreenCapture(resultCode, data)

        signalingClient = SignalingClient(serverUrl, this)
        signalingClient?.connect()
    }

    // Signaling Listener Callbacks
    override fun onConnected() {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        signalingClient?.registerHost(currentSessionId, currentPin, deviceName, null)
    }

    override fun onRegistered(id: String, pin: String) {
        Log.i(TAG, "Screen sharing registered successfully with server. ID: $id")
    }

    override fun onIncomingConnection(controllerId: String, controllerName: String) {
        Log.i(TAG, "Incoming connection from controller: $controllerName ($controllerId)")
        activeControllerId = controllerId

        // Initialize PeerConnection with Google STUN servers
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        webRtcClient?.createPeerConnection(iceServers)
    }

    override fun onOfferReceived(fromId: String, sdp: SdpPayload) {
        activeControllerId = fromId
        webRtcClient?.handleRemoteOffer(sdp) { answerSdp ->
            signalingClient?.sendAnswer(fromId, answerSdp)
        }
    }

    override fun onAnswerReceived(fromId: String, sdp: SdpPayload) {}

    override fun onIceCandidateReceived(fromId: String, candidate: IcePayload) {
        webRtcClient?.addRemoteIceCandidate(candidate)
    }

    override fun onRemoteInputReceived(action: String, x: Float, y: Float) {
        if (!allowControl) return
        RemoteControlAccessibilityService.getInstance()?.dispatchTouch(action, x, y)
    }

    override fun onRemoteCommandReceived(command: String) {
        if (!allowControl) return
        RemoteControlAccessibilityService.getInstance()?.performNavCommand(command)
    }

    override fun onError(reason: String) {
        Log.e(TAG, "Signaling Error: $reason")
    }

    override fun onDisconnected() {
        Log.w(TAG, "Signaling Disconnected")
    }

    // WebRTC Listener Callbacks
    override fun onIceCandidateCreated(candidate: IcePayload) {
        activeControllerId?.let { targetId ->
            signalingClient?.sendIceCandidate(targetId, candidate)
        }
    }

    override fun onRemoteNavCommand(command: String) {
        if (!allowControl) return
        RemoteControlAccessibilityService.getInstance()?.performNavCommand(command)
    }

    override fun onFileChunkReceived(data: ByteArray) {
        Log.i(TAG, "Received file chunk of size: ${data.size} bytes")
    }

    private fun stopSharing() {
        webRtcClient?.stop()
        webRtcClient = null
        signalingClient?.disconnect()
        signalingClient = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSharing()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_screen_sharing_title))
            .setContentText(getString(R.string.notif_screen_sharing_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.action_stop_sharing), stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
