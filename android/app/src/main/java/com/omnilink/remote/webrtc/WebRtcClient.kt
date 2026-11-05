package com.omnilink.remote.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.omnilink.remote.model.IcePayload
import com.omnilink.remote.model.RemoteInputPacket
import com.omnilink.remote.model.RemoteNavCommand
import com.omnilink.remote.model.SdpPayload
import org.webrtc.*
import java.nio.charset.StandardCharsets

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("OmniSdp", "SDP Create Failure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("OmniSdp", "SDP Set Failure: $error")
    }
}

class WebRtcClient(
    private val context: Context,
    private val listener: WebRtcListener
) {
    interface WebRtcListener {
        fun onIceCandidateCreated(candidate: IcePayload)
        fun onRemoteInputReceived(action: String, x: Float, y: Float)
        fun onRemoteNavCommand(command: String)
        fun onFileChunkReceived(data: ByteArray)
    }

    companion object {
        private const val TAG = "OmniWebRtc"
    }

    private val gson = Gson()
    val eglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var dataChannel: DataChannel? = null

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun startScreenCapture(resultCode: Int, data: Intent, width: Int = 1080, height: Int = 1920, fps: Int = 60) {
        videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection Stopped by Android OS")
            }
        })

        surfaceTextureHelper = SurfaceTextureHelper.create("OmniScreenCaptureThread", eglBase.eglBaseContext)
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer!!.startCapture(width, height, fps)

        videoTrack = peerConnectionFactory.createVideoTrack("OMNI_VIDEO_TRACK", videoSource)
        videoTrack?.setEnabled(true)
        Log.i(TAG, "Hardware Screen Capture started at ${width}x${height} @ ${fps}fps")
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {
                if (candidate != null) {
                    listener.onIceCandidateCreated(
                        IcePayload(
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            candidate = candidate.sdp
                        )
                    )
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                dataChannel = dc
                setupDataChannel(dc)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE Connection State: $state")
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        videoTrack?.let {
            peerConnection?.addTrack(it, listOf("OMNI_STREAM"))
        }
    }

    private fun setupDataChannel(dc: DataChannel?) {
        dc?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}
            override fun onStateChange() {
                Log.i(TAG, "DataChannel State: ${dc.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                if (buffer.binary) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    listener.onFileChunkReceived(bytes)
                } else {
                    val text = StandardCharsets.UTF_8.decode(buffer.data).toString()
                    handleDataChannelMessage(text)
                }
            }
        })
    }

    private fun handleDataChannelMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            val type = json.get("t")?.asString ?: return

            if (type == "input") {
                val input = gson.fromJson(text, RemoteInputPacket::class.java)
                listener.onRemoteInputReceived(input.action, input.x, input.y)
            } else if (type == "nav") {
                val nav = gson.fromJson(text, RemoteNavCommand::class.java)
                listener.onRemoteNavCommand(nav.command)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing data channel packet: $text", e)
        }
    }

    fun handleRemoteOffer(remoteSdp: SdpPayload, onAnswerCreated: (SdpPayload) -> Unit) {
        val description = SessionDescription(SessionDescription.Type.OFFER, remoteSdp.description)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                }
                peerConnection?.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc != null) {
                            peerConnection?.setLocalDescription(object : SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    onAnswerCreated(
                                        SdpPayload(
                                            type = answerDesc.type.canonicalForm(),
                                            description = answerDesc.description
                                        )
                                    )
                                }
                            }, answerDesc)
                        }
                    }
                }, constraints)
            }
        }, description)
    }

    fun addRemoteIceCandidate(ice: IcePayload) {
        val candidate = org.webrtc.IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.candidate)
        peerConnection?.addIceCandidate(candidate)
    }

    fun stop() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            videoTrack?.dispose()
            videoTrack = null
            dataChannel?.close()
            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebRtcClient", e)
        }
    }
}
