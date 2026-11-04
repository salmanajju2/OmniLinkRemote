package com.omnilink.remote.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * RemoteControlAccessibilityService
 * Responsible for injecting remote touches, swipes, scrolls, and system navigation gestures.
 * Complies with Google Play Accessibility Guidelines by explicitly operating only during user sessions.
 */
class RemoteControlAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OmniAccessibility"
        private var instance: RemoteControlAccessibilityService? = null

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected

        fun getInstance(): RemoteControlAccessibilityService? = instance
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "OmniLink Remote Accessibility Service Connected & Ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We do not snoop on content to protect privacy; only gesture injection is used.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceConnected.value = false
        Log.i(TAG, "Accessibility Service Destroyed")
    }

    /**
     * Dispatches simulated touch events based on percentage coordinates (0.0f - 1.0f)
     */
    fun dispatchTouch(action: String, xPercent: Float, yPercent: Float) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val actualX = (xPercent * screenWidth).coerceIn(0f, screenWidth)
        val actualY = (yPercent * screenHeight).coerceIn(0f, screenHeight)

        when (action) {
            "CLICK" -> performTap(actualX, actualY)
            "DOWN" -> {
                lastTouchX = actualX
                lastTouchY = actualY
                isDragging = true
            }
            "MOVE" -> {
                if (isDragging) {
                    performDrag(lastTouchX, lastTouchY, actualX, actualY, duration = 25)
                    lastTouchX = actualX
                    lastTouchY = actualY
                }
            }
            "UP" -> {
                if (isDragging) {
                    performTap(actualX, actualY)
                    isDragging = false
                }
            }
            "SCROLL_UP" -> {
                // Swipe from lower screen to higher screen
                val startY = screenHeight * 0.7f
                val endY = screenHeight * 0.3f
                performDrag(actualX, startY, actualX, endY, duration = 120)
            }
            "SCROLL_DOWN" -> {
                // Swipe from higher screen to lower screen
                val startY = screenHeight * 0.3f
                val endY = screenHeight * 0.7f
                performDrag(actualX, startY, actualX, endY, duration = 120)
            }
        }
    }

    /**
     * Performs a single fast tap gesture at the specified screen pixel coordinates
     */
    private fun performTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Performs a smooth drag/swipe gesture between two coordinates
     */
    private fun performDrag(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(10))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * System Navigation Gestures
     */
    fun performNavCommand(command: String) {
        when (command) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "LOCK" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
        }
    }
}
