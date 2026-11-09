package com.porteiro.server.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Extrai embeddings faciais.
 *
 * O modelo MobileFaceNet (112x112, 192 dimensões) deve ser colocado em
 * `app/src/main/assets/mobilefacenet.tflite`. Sem o modelo, o reconhecimento
 * automático permanece desativado por segurança; o cadastro geométrico de
 * emergência pode ser habilitado via [ALLOW_GEOMETRIC_FALLBACK] apenas para
 * desenvolvimento.
 */
class FaceEmbedder(private val context: Context) {

    private val interpreter: Interpreter? = loadInterpreter()
    private val inputHeight: Int
    private val inputWidth: Int
    private val outputSize: Int

    val modelAvailable: Boolean get() = interpreter != null

    init {
        val inputShape = interpreter?.getInputTensor(0)?.shape()
        inputHeight = inputShape?.getOrNull(1) ?: 112
        inputWidth = inputShape?.getOrNull(2) ?: 112
        outputSize = interpreter?.getOutputTensor(0)?.shape()?.lastOrNull() ?: 192
    }

    private fun loadInterpreter(): Interpreter? = runCatching {
        val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
        Interpreter(buffer, Interpreter.Options().apply { numThreads = 2 })
    }.getOrNull()

    fun embed(bitmap: Bitmap, face: Face): FloatArray? {
        val engine = interpreter
        if (engine == null) {
            return if (ALLOW_GEOMETRIC_FALLBACK) geometricEmbedding(face) else null
        }
        return runCatching {
            val crop = cropWithMargin(bitmap, face.boundingBox) ?: return null
            val resized = Bitmap.createScaledBitmap(crop, inputWidth, inputHeight, true)
            val input = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(inputWidth * inputHeight)
            resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF)
                val g = ((pixel shr 8) and 0xFF)
                val b = (pixel and 0xFF)
                input.putFloat((r - 127.5f) / 128f)
                input.putFloat((g - 127.5f) / 128f)
                input.putFloat((b - 127.5f) / 128f)
            }
            input.rewind()
            val output = Array(1) { FloatArray(outputSize) }
            engine.run(input, output)
            l2Normalize(output[0])
        }.getOrNull()
    }

    private fun cropWithMargin(bitmap: Bitmap, box: Rect): Bitmap? {
        if (box.width() <= 0 || box.height() <= 0) return null
        val marginX = (box.width() * 0.18f).toInt()
        val marginY = (box.height() * 0.22f).toInt()
        val left = (box.left - marginX).coerceAtLeast(0)
        val top = (box.top - marginY).coerceAtLeast(0)
        val right = (box.right + marginX).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + marginY).coerceAtMost(bitmap.height)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun geometricEmbedding(face: Face): FloatArray? {
        val box = face.boundingBox
        if (box.width() <= 0 || box.height() <= 0) return null
        val landmarks = face.allLandmarks
        if (landmarks.isEmpty()) return null
        val values = ArrayList<Float>(landmarks.size * 2 + 8)
        landmarks.forEach { landmark ->
            values.add((landmark.position.x - box.left) / box.width())
            values.add((landmark.position.y - box.top) / box.height())
        }
        for (i in landmarks.indices) {
            for (j in i + 1 until landmarks.size) {
                val dx = landmarks[i].position.x - landmarks[j].position.x
                val dy = landmarks[i].position.y - landmarks[j].position.y
                values.add(sqrt(dx * dx + dy * dy) / box.width())
                if (values.size >= landmarks.size * 2 + 16) break
            }
        }
        return l2Normalize(values.toFloatArray())
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (value in vector) sum += value * value
        val norm = sqrt(sum).toFloat().coerceAtLeast(1e-6f)
        for (i in vector.indices) vector[i] = vector[i] / norm
        return vector
    }

    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    companion object {
        private const val MODEL_FILE = "mobilefacenet.tflite"

        /** Apenas para desenvolvimento. Desative em produção. */
        const val ALLOW_GEOMETRIC_FALLBACK = false
    }
}
