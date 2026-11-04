package com.omnilink.remote

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.gson.Gson
import com.omnilink.remote.model.IcePayload
import com.omnilink.remote.model.RemoteInputPacket
import com.omnilink.remote.model.RemoteNavCommand
import com.omnilink.remote.model.SdpPayload
import com.omnilink.remote.webrtc.SignalingClient
import org.webrtc.*

/**
 * RemotePlayerActivity
 * Phone-to-Phone Controller Screen:
 * Displays the remote phone's screen and transmits touch events & system navigation back in real-time.
 */
class RemotePlayerActivity : ComponentActivity(), SignalingClient.SignalingListener {

    companion object {
        private const val TAG = "OmniPlayer"
        const val EXTRA_PARTNER_ID = "EXTRA_PARTNER_ID"
        const val EXTRA_PIN = "EXTRA_PIN"
        const val EXTRA_SERVER_URL = "EXTRA_SERVER_URL"
    }

    private lateinit var partnerId: String
    private var partnerPin: String? = null
    private lateinit var serverUrl: String

    private val gson = Gson()
    private var signalingClient: SignalingClient? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var inputDataChannel: DataChannel? = null

    private lateinit var eglBase: EglBase
    private lateinit var surfaceViewRenderer: SurfaceViewRenderer
    private lateinit var loadingProgress: ProgressBar
    private lateinit var deviceNameLabel: TextView

    private var lastTouchSent = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        partnerId = intent.getStringExtra(EXTRA_PARTNER_ID) ?: ""
        partnerPin = intent.getStringExtra(EXTRA_PIN)
        serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: "http://192.168.1.100:8080"

        if (partnerId.isEmpty()) {
            Toast.makeText(this, "Invalid Partner ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        initWebRtc()
        connectSignaling()
    }

    private fun setupUI() {
        // Build interactive viewer layout programmatically for maximum compatibility
        val rootLayout = android.widget.RelativeLayout(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
        }

        eglBase = EglBase.create()
        surfaceViewRenderer = SurfaceViewRenderer(this).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }

        val rendererParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(surfaceViewRenderer, rendererParams)

        // Loading Progress
        loadingProgress = ProgressBar(this).apply {
            val progressParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = progressParams
        }
        rootLayout.addView(loadingProgress)

        // Top Header
        val topBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1E293B.toInt())
            setPadding(30, 20, 30, 20)
            val topParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            }
            layoutParams = topParams
        }

        deviceNameLabel = TextView(this).apply {
            text = "रिमोट डिवाइस: $partnerId"
            setTextColor(0xFFF8FAFC.toInt())
            textSize = 15f
            val textParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            layoutParams = textParams
        }
        topBar.addView(deviceNameLabel)

        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFEF4444.toInt())
            setOnClickListener { finish() }
        }
        topBar.addView(btnClose)
        rootLayout.addView(topBar)

        // Bottom Navigation Bar (Phone-to-Phone Back, Home, Recents control)
        val bottomBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(0xDD1E293B.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(20, 16, 20, 16)
            val bottomParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            layoutParams = bottomParams
        }

        val btnBack = createNavButton(android.R.drawable.ic_media_previous) { sendNavCommand("BACK") }
        val btnHome = createNavButton(android.R.drawable.radiobutton_off_background) { sendNavCommand("HOME") }
        val btnRecents = createNavButton(android.R.drawable.ic_menu_agenda) { sendNavCommand("RECENTS") }

        bottomBar.addView(btnBack)
        bottomBar.addView(btnHome)
        bottomBar.addView(btnRecents)
        rootLayout.addView(bottomBar)

        // Setup Touch Listener for Phone-to-Phone Screen Control
        setupTouchListener()

        setContentView(rootLayout)
    }

    private fun createNavButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            setBackgroundColor(0x33334155)
            setColorFilter(0xFFF8FAFC.toInt())
            setPadding(30, 20, 30, 20)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 0, 20, 0)
            }
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun setupTouchListener() {
        surfaceViewRenderer.setOnTouchListener { _, event ->
            val viewWidth = surfaceViewRenderer.width.toFloat()
            val viewHeight = surfaceViewRenderer.height.toFloat()

            if (viewWidth <= 0 || viewHeight <= 0) return@setOnTouchListener false

            val normX = (event.x / viewWidth).coerceIn(0f, 1f)
            val normY = (event.y / viewHeight).coerceIn(0f, 1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sendInput("DOWN", normX, normY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val now = System.currentTimeMillis()
                    if (now - lastTouchSent > 16) { // 60 FPS throttle
                        lastTouchSent = now
                        sendInput("MOVE", normX, normY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    sendInput("UP", normX, normY)
                    true
                }
                else -> false
            }
        }
    }

    private fun sendInput(action: String, x: Float, y: Float) {
        val packet = RemoteInputPacket(action = action, x = x, y = y)
        val json = gson.toJson(packet)

        if (inputDataChannel != null && inputDataChannel?.state() == DataChannel.ChannelState.OPEN) {
            val buffer = DataChannel.Buffer(java.nio.ByteBuffer.wrap(json.toByteArray()), false)
            inputDataChannel?.send(buffer)
        }
    }

    private fun sendNavCommand(command: String) {
        val packet = RemoteNavCommand(command = command)
        val json = gson.toJson(packet)

        if (inputDataChannel != null && inputDataChannel?.state() == DataChannel.ChannelState.OPEN) {
            val buffer = DataChannel.Buffer(java.nio.ByteBuffer.wrap(json.toByteArray()), false)
            inputDataChannel?.send(buffer)
        }
    }

    private fun initWebRtc() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(encoderFactory)
            .createPeerConnectionFactory()
    }

    private fun connectSignaling() {
        signalingClient = SignalingClient(serverUrl, this)
        signalingClient?.connect()
    }

    // Signaling Callbacks
    override fun onConnected() {
        runOnUiThread {
            Toast.makeText(this, "Connecting to device $partnerId...", Toast.LENGTH_SHORT).show()
        }
        val connectReq = com.omnilink.remote.model.SignalingMessage.ConnectRequest(
            targetId = partnerId.replace("\\s".toRegex(), ""),
            pin = partnerPin,
            clientName = "Android Mobile Controller"
        )
    }

    override fun onRegistered(id: String, pin: String) {}
    override fun onIncomingConnection(controllerId: String, controllerName: String) {}

    override fun onOfferReceived(fromId: String, sdp: SdpPayload) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {
                if (candidate != null) {
                    signalingClient?.sendIceCandidate(
                        fromId,
                        IcePayload(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                    )
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (receiver?.track() is VideoTrack) {
                    val videoTrack = receiver.track() as VideoTrack
                    runOnUiThread {
                        videoTrack.addSink(surfaceViewRenderer)
                        loadingProgress.visibility = View.GONE
                        Toast.makeText(this@RemotePlayerActivity, "Screen Connected Live!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                inputDataChannel = dc
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
        })

        // Create touch data channel if not created by host
        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = 0
        }
        inputDataChannel = peerConnection?.createDataChannel("omni-input", init)

        val desc = SessionDescription(SessionDescription.Type.OFFER, sdp.description)
        peerConnection?.setRemoteDescription(object : WebRtcClient.SdpObserverAdapter() {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : WebRtcClient.SdpObserverAdapter() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        if (answer != null) {
                            peerConnection?.setLocalDescription(object : WebRtcClient.SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    signalingClient?.sendAnswer(
                                        fromId,
                                        SdpPayload(answer.type.canonicalForm(), answer.description)
                                    )
                                }
                            }, answer)
                        }
                    }
                }, MediaConstraints())
            }
        }, desc)
    }

    override fun onAnswerReceived(fromId: String, sdp: SdpPayload) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp.description)
        peerConnection?.setRemoteDescription(WebRtcClient.SdpObserverAdapter(), desc)
    }

    override fun onIceCandidateReceived(fromId: String, candidate: IcePayload) {
        val ice = org.webrtc.IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
        peerConnection?.addIceCandidate(ice)
    }

    override fun onRemoteInputReceived(action: String, x: Float, y: Float) {}
    override fun onRemoteCommandReceived(command: String) {}

    override fun onError(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection Error: $reason", Toast.LENGTH_LONG).show()
            loadingProgress.visibility = View.GONE
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            Toast.makeText(this, "Device Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inputDataChannel?.close()
        peerConnection?.close()
        surfaceViewRenderer.release()
        eglBase.release()
        signalingClient?.disconnect()
    }
}
