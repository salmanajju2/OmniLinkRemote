package com.omnilink.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.omnilink.remote.service.HostBackgroundService
import com.omnilink.remote.service.RemoteControlAccessibilityService
import com.omnilink.remote.service.ScreenCaptureService
import com.omnilink.remote.ui.screens.OmniMainScreen
import com.omnilink.remote.ui.theme.OmniLinkTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var mySessionId by mutableStateOf("")
    private var myPin by mutableStateOf("")
    private var isSharingActive by mutableStateOf(false)
    private var isUnattendedActive by mutableStateOf(false)
    private var allowControlRequested by mutableStateOf(true)
    private var currentServerUrl by mutableStateOf("http://16.178.122.82:8080")

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenSharingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission declined", Toast.LENGTH_SHORT).show()
            isSharingActive = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        loadOrCreateSessionCredentials()

        setContent {
            OmniLinkTheme {
                val isAccessibilityConnected by RemoteControlAccessibilityService.isServiceConnected.collectAsState()

                OmniMainScreen(
                    myId = mySessionId,
                    myPin = myPin,
                    serverUrl = currentServerUrl,
                    isSharingActive = isSharingActive,
                    isAccessibilityEnabled = isAccessibilityConnected,
                    isUnattendedEnabled = isUnattendedActive,
                    onStartSharing = { allowControl ->
                        allowControlRequested = allowControl
                        requestScreenCapture()
                    },
                    onStopSharing = {
                        stopScreenSharingService()
                    },
                    onConnectToPartner = { partnerId, pinOrPass, isLowLatency ->
                        connectToRemoteDevice(partnerId, pinOrPass, isLowLatency)
                    },
                    onToggleUnattended = { enable, password ->
                        toggleUnattendedHost(enable, password)
                    },
                    onRequestAccessibility = {
                        openAccessibilitySettings()
                    },
                    onSaveServerUrl = { newUrl ->
                        val prefs = getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("server_url", newUrl).apply()
                        currentServerUrl = newUrl
                        Toast.makeText(this, "Server URL updated: $newUrl", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun loadOrCreateSessionCredentials() {
        val prefs = getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("session_id", null)
        if (id == null) {
            val raw = (100000000 + Random.nextInt(900000000)).toString()
            id = "${raw.slice(0..2)} ${raw.slice(3..5)} ${raw.slice(6..8)}"
            prefs.edit().putString("session_id", id).apply()
        }
        mySessionId = id
        myPin = (1000 + Random.nextInt(9000)).toString()
        isUnattendedActive = prefs.getBoolean("unattended_enabled", false)
        currentServerUrl = prefs.getString("server_url", "http://16.178.122.82:8080") ?: "http://16.178.122.82:8080"
    }

    private fun requestScreenCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startScreenSharingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_SESSION_ID, mySessionId)
            putExtra(ScreenCaptureService.EXTRA_PIN, myPin)
            putExtra(ScreenCaptureService.EXTRA_SERVER_URL, currentServerUrl)
            putExtra(ScreenCaptureService.EXTRA_ALLOW_CONTROL, allowControlRequested)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isSharingActive = true
    }

    private fun stopScreenSharingService() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(serviceIntent)
        isSharingActive = false
    }

    private fun connectToRemoteDevice(partnerId: String, pinOrPass: String, isLowLatency: Boolean) {
        val cleanId = partnerId.replace("\\s".toRegex(), "")
        if (cleanId.length < 9) {
            Toast.makeText(this, "Please enter a valid 9-digit Partner ID", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, RemotePlayerActivity::class.java).apply {
            putExtra(RemotePlayerActivity.EXTRA_PARTNER_ID, cleanId)
            putExtra(RemotePlayerActivity.EXTRA_PIN, pinOrPass)
            putExtra(RemotePlayerActivity.EXTRA_SERVER_URL, currentServerUrl)
        }
        startActivity(intent)
    }

    private fun toggleUnattendedHost(enable: Boolean, password: String) {
        val prefs = getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
        if (enable && password.length < 4) {
            Toast.makeText(this, "Please set a password with at least 4 characters", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putBoolean("unattended_enabled", enable)
            .putString("unattended_id", mySessionId)
            .putString("unattended_pass", password)
            .apply()

        isUnattendedActive = enable

        val intent = Intent(this, HostBackgroundService::class.java).apply {
            action = if (enable) HostBackgroundService.ACTION_START_HOST else HostBackgroundService.ACTION_STOP_HOST
            putExtra(HostBackgroundService.EXTRA_HOST_ID, mySessionId)
            putExtra(HostBackgroundService.EXTRA_PASSWORD, password)
            putExtra(HostBackgroundService.EXTRA_SERVER_URL, currentServerUrl)
        }

        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "24/7 Unattended Host Enabled", Toast.LENGTH_SHORT).show()
        } else {
            startService(intent)
            Toast.makeText(this, "24/7 Unattended Host Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Locate and enable 'OmniLink Remote Control Helper'", Toast.LENGTH_LONG).show()
    }
}
