package com.omnilink.remote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
            val isUnattendedEnabled = prefs.getBoolean("unattended_enabled", false)
            val hostId = prefs.getString("unattended_id", "") ?: ""
            val password = prefs.getString("unattended_pass", "") ?: ""
            val serverUrl = prefs.getString("server_url", "http://192.168.1.100:3000") ?: ""

            if (isUnattendedEnabled && hostId.isNotEmpty() && password.isNotEmpty()) {
                val serviceIntent = Intent(context, HostBackgroundService::class.java).apply {
                    action = HostBackgroundService.ACTION_START_HOST
                    putExtra(HostBackgroundService.EXTRA_HOST_ID, hostId)
                    putExtra(HostBackgroundService.EXTRA_PASSWORD, password)
                    putExtra(HostBackgroundService.EXTRA_SERVER_URL, serverUrl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
