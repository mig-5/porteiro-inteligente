package com.porteiro.server.webrtc

import android.content.Context
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Fonte única de vídeo da portaria. A mesma captura alimenta:
 *  - o WebRTC durante as chamadas;
 *  - o reconhecimento facial contínuo (RF-05);
 *  - a pré-visualização na tela da portaria.
 */
class CameraStream(private val context: Context) {

    private val eglBase: EglBase = WebRtcCore.captureEglBase
    private var capturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var started = false

    @Volatile
    var frameListener: ((VideoFrame) -> Unit)? = null

    val track: VideoTrack get() = videoTrack ?: error("câmera não inicializada")

    fun initialize(): VideoTrack {
        videoTrack?.let { return it }
        val factory = WebRtcCore.requireFactory()
        val enumerator = Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: error("Nenhuma câmera disponível")

        val createdCapturer = enumerator.createCapturer(deviceName, null)
            ?: error("Não foi possível abrir a câmera")
        val helper = SurfaceTextureHelper.create("PortariaCapture", eglBase.eglBaseContext)
        val source = factory.createVideoSource(createdCapturer.isScreencast)
        val track = factory.createVideoTrack("portaria-video", source)

        createdCapturer.initialize(helper, context, source.capturerObserver)
        track.addSink(object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                frameListener?.invoke(frame)
            }
        })

        capturer = createdCapturer
        surfaceTextureHelper = helper
        videoSource = source
        videoTrack = track
        return track
    }

    fun start() {
        if (started) return
        capturer?.startCapture(640, 480, 24)
        started = true
    }

    fun stop() {
        if (!started) return
        runCatching { capturer?.stopCapture() }
        started = false
    }

    fun addSink(sink: VideoSink) {
        videoTrack?.addSink(sink)
    }

    fun removeSink(sink: VideoSink) {
        videoTrack?.removeSink(sink)
    }

    fun release() {
        stop()
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { capturer?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        videoTrack = null
        videoSource = null
        capturer = null
        surfaceTextureHelper = null
    }
}
