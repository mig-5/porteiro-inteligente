package com.porteiro.server.face

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.porteiro.server.data.AccessStatus
import com.porteiro.server.data.PortariaRepository
import com.porteiro.server.data.TriggerType
import com.porteiro.server.network.AccessController
import com.porteiro.server.network.AccessOutcome
import com.porteiro.server.network.FaceEnrollResult
import com.porteiro.server.settings.ServerSettings
import com.porteiro.server.settings.ServerSettingsStore
import com.porteiro.server.webrtc.CameraStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoFrame
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class FaceEngineState(
    val running: Boolean = false,
    val modelAvailable: Boolean = false,
    val detectedResidentName: String? = null,
    val message: String? = null,
    val lockedUntil: Long = 0L
) {
    fun isLocked(now: Long = System.currentTimeMillis()): Boolean = now < lockedUntil
}

/**
 * Pipeline de reconhecimento facial local (RF-05 / RNF-01 / RN-03).
 *
 * A mesma câmera usada nas chamadas alimenta a detecção contínua. Os frames são
 * amostrados a cada ~700ms e comparados com os embeddings do banco SQLite.
 */
class FaceRecognitionEngine(
    context: Context,
    private val repository: PortariaRepository,
    private val accessController: AccessController,
    private val camera: CameraStream,
    private val settingsStore: ServerSettingsStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.18f)
            .enableTracking()
            .build()
    )
    private val embedder = FaceEmbedder(context)

    private val _state = MutableStateFlow(FaceEngineState(modelAvailable = embedder.modelAvailable))
    val state: StateFlow<FaceEngineState> = _state.asStateFlow()

    private val processing = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private var lockedUntil = 0L
    private val failureTimes = ArrayDeque<Long>()
    private var lastFailureLoggedAt = 0L
    private val lastUnlockAt = HashMap<Long, Long>()

    @Volatile
    private var currentSettings: ServerSettings? = null

    private var cache: List<Pair<Long, FloatArray>> = emptyList()
    private var cacheAt = 0L
    private var running = false
    private var settingsJob: Job? = null

    val modelAvailable: Boolean get() = embedder.modelAvailable

    fun start() {
        if (running) return
        running = true
        settingsJob = scope.launch {
            settingsStore.settings.collect { currentSettings = it }
        }
        camera.frameListener = { frame -> offerFrame(frame) }
        runCatching { camera.start() }
        _state.update { it.copy(running = true, modelAvailable = embedder.modelAvailable) }
    }

    fun stop() {
        running = false
        settingsJob?.cancel()
        settingsJob = null
        camera.frameListener = null
        _state.update { it.copy(running = false) }
    }

    fun invalidateCache() {
        cacheAt = 0L
    }

    private fun offerFrame(frame: VideoFrame) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 700) return
        if (!processing.compareAndSet(false, true)) return
        lastFrameAt = now
        frame.retain()
        scope.launch {
            try {
                processFrame(frame)
            } finally {
                frame.release()
                processing.set(false)
            }
        }
    }

    private suspend fun processFrame(frame: VideoFrame) {
        val settings = currentSettings ?: settingsStore.settings.first()
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return

        val bitmap = FrameConverter.frameToBitmap(frame) ?: return
        val faces = runCatching {
            Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 1500, TimeUnit.MILLISECONDS)
        }.getOrNull() ?: return
        if (faces.isEmpty()) return
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return

        if (!embedder.modelAvailable) {
            _state.update {
                it.copy(
                    modelAvailable = false,
                    message = "Modelo biométrico ausente (assets/mobilefacenet.tflite)"
                )
            }
            return
        }

        val vector = embedder.embed(bitmap, face) ?: return
        val known = loadEmbeddings()
        var bestResident: Long? = null
        var bestScore = 0f
        for ((residentId, stored) in known) {
            val score = embedder.similarity(vector, stored)
            if (score > bestScore) {
                bestScore = score
                bestResident = residentId
            }
        }

        if (bestResident != null && bestScore >= settings.similarityThreshold) {
            handleMatch(bestResident, bestScore, settings)
        } else {
            handleFailure(now, bestScore)
        }
    }

    private suspend fun handleMatch(residentId: Long, score: Float, settings: ServerSettings) {
        val resident = repository.residentById(residentId) ?: return
        val now = System.currentTimeMillis()
        val last = lastUnlockAt[residentId] ?: 0L
        if (now - last < UNLOCK_COOLDOWN_MS) {
            _state.update { it.copy(detectedResidentName = resident.name) }
            return
        }
        failureTimes.clear()
        lastUnlockAt[residentId] = now

        _state.update {
            it.copy(
                detectedResidentName = resident.name,
                message = "Morador identificado: ${resident.name} (${(score * 100).toInt()}%)",
                lockedUntil = 0L
            )
        }

        if (settings.autoUnlockEnabled) {
            when (accessController.unlockForResident(residentId, TriggerType.FACIAL_RECOGNITION)) {
                AccessOutcome.Success -> _state.update { it.copy(message = "Acesso liberado para ${resident.name}") }
                AccessOutcome.GatewayFailure -> _state.update { it.copy(message = "Falha ao acionar o portão") }
                AccessOutcome.Unauthorized -> Unit
            }
        }
    }

    private suspend fun handleFailure(now: Long, score: Float) {
        failureTimes.addLast(now)
        while (failureTimes.isNotEmpty() && now - failureTimes.first() > FAILURE_WINDOW_MS) {
            failureTimes.removeFirst()
        }
        if (now - lastFailureLoggedAt > 5000) {
            lastFailureLoggedAt = now
            repository.log(null, TriggerType.FACIAL_RECOGNITION, AccessStatus.FAILED_FACE_NOT_MATCH)
        }
        if (failureTimes.size >= MAX_FAILURES) {
            failureTimes.clear()
            lockedUntil = now + LOCKOUT_MS
            _state.update {
                it.copy(
                    lockedUntil = lockedUntil,
                    detectedResidentName = null,
                    message = "Reconhecimento facial bloqueado por 30s. Disque manualmente."
                )
            }
        } else {
            _state.update {
                it.copy(
                    detectedResidentName = null,
                    message = "Rosto não reconhecido (${(score * 100).toInt()}%)"
                )
            }
        }
    }

    private suspend fun loadEmbeddings(): List<Pair<Long, FloatArray>> {
        val now = SystemClock.elapsedRealtime()
        if (now - cacheAt < CACHE_TTL_MS) return cache
        val loaded = runCatching {
            repository.facialEmbeddings().mapNotNull { embedding ->
                val vector = PortariaRepository.decodeVector(embedding.vectorData)
                if (vector.isEmpty()) null else embedding.residentId to vector
            }
        }.getOrDefault(emptyList())
        cache = loaded
        cacheAt = now
        return loaded
    }

    suspend fun enroll(residentId: Long, jpeg: ByteArray): FaceEnrollResult {
        if (repository.residentById(residentId) == null) return FaceEnrollResult.Unauthorized
        if (!embedder.modelAvailable) return FaceEnrollResult.Unavailable
        val bitmap = FrameConverter.decodeJpeg(jpeg) ?: return FaceEnrollResult.NoFaceDetected
        val faces = runCatching {
            Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 1500, TimeUnit.MILLISECONDS)
        }.getOrNull() ?: return FaceEnrollResult.NoFaceDetected
        val face: Face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return FaceEnrollResult.NoFaceDetected
        val vector = embedder.embed(bitmap, face) ?: return FaceEnrollResult.NoFaceDetected
        repository.addFacialEmbedding(residentId, vector)
        invalidateCache()
        _state.update { it.copy(message = "Biometria facial cadastrada") }
        return FaceEnrollResult.Success
    }

    fun release() {
        stop()
        runCatching { detector.close() }
    }

    companion object {
        const val MAX_FAILURES = 3
        const val FAILURE_WINDOW_MS = 30_000L
        const val LOCKOUT_MS = 30_000L
        const val UNLOCK_COOLDOWN_MS = 8_000L
        const val CACHE_TTL_MS = 5_000L
    }
}
