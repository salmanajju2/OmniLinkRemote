package com.omnilink.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omnilink.remote.model.IcePayload
import com.omnilink.remote.model.SdpPayload
import com.omnilink.remote.webrtc.SignalingClient

/**
 * HostBackgroundService
 * Keeps the device registered with the signaling server 24/7 for unattended access with static password.
 */
class HostBackgroundService : Service(), SignalingClient.SignalingListener {

    companion object {
        private const val TAG = "OmniHostService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "omni_host_channel"

        const val ACTION_START_HOST = "ACTION_START_HOST"
        const val ACTION_STOP_HOST = "ACTION_STOP_HOST"

        const val EXTRA_HOST_ID = "EXTRA_HOST_ID"
        const val EXTRA_PASSWORD = "EXTRA_PASSWORD"
        const val EXTRA_SERVER_URL = "EXTRA_SERVER_URL"
    }

    private var signalingClient: SignalingClient? = null
    private var hostId: String = ""
    private var password: String = ""
    private var serverUrl: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_START_HOST -> {
                hostId = intent.getStringExtra(EXTRA_HOST_ID) ?: ""
                password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
                serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: "http://192.168.1.100:3000"

                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildHostNotification())

                signalingClient = SignalingClient(serverUrl, this)
                signalingClient?.connect()
            }
            ACTION_STOP_HOST -> {
                signalingClient?.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onConnected() {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (24/7 Host)"
        signalingClient?.registerHost(hostId, "STATIC", deviceName, password)
        Log.i(TAG, "Unattended host registered with master password")
    }

    override fun onRegistered(id: String, pin: String) {}
    override fun onIncomingConnection(controllerId: String, controllerName: String) {}
    override fun onOfferReceived(fromId: String, sdp: SdpPayload) {}
    override fun onAnswerReceived(fromId: String, sdp: SdpPayload) {}
    override fun onIceCandidateReceived(fromId: String, candidate: IcePayload) {}
    override fun onRemoteInputReceived(action: String, x: Float, y: Float) {}
    override fun onRemoteCommandReceived(command: String) {}
    override fun onError(reason: String) {}
    override fun onDisconnected() {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniLink 24/7 Host Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildHostNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniLink 24/7 Unattended Access Active")
            .setContentText("यह डिवाइस किसी भी समय दूरस्थ रूप से एक्सेस किया जा सकता है")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        signalingClient?.disconnect()
    }
}
