package com.porteiro.client.webrtc

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/** Inicialização única do WebRTC (factory + contexto EGL de captura). */
object WebRtcCore {
    private var factory: PeerConnectionFactory? = null

    val captureEglBase: EglBase by lazy { EglBase.create() }

    @Synchronized
    fun initialize(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(captureEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(captureEglBase.eglBaseContext)
        val created = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        factory = created
        return created
    }

    fun requireFactory(): PeerConnectionFactory =
        factory ?: error("WebRtcCore.initialize() não foi chamado")

    fun newRendererEglBase(): EglBase = EglBase.create()
}
