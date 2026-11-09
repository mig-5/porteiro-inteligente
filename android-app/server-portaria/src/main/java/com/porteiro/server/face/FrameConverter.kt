package com.porteiro.server.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import org.webrtc.VideoFrame
import java.io.ByteArrayOutputStream

object FrameConverter {

    fun frameToBitmap(frame: VideoFrame): Bitmap? {
        return runCatching {
            val i420 = frame.buffer.toI420() ?: return null
            try {
                val width = i420.width
                val height = i420.height
                val ySize = width * height
                val uvSize = (width / 2) * (height / 2)
                val nv21 = ByteArray(ySize + uvSize * 2)

                val y = i420.dataY
                val strideY = i420.strideY
                for (row in 0 until height) {
                    y.position(row * strideY)
                    y.get(nv21, row * width, width)
                }
                val u = i420.dataU
                val v = i420.dataV
                val strideUv = i420.strideU
                val halfWidth = width / 2
                val halfHeight = height / 2
                for (row in 0 until halfHeight) {
                    for (col in 0 until halfWidth) {
                        val vIndex = row * strideUv + col
                        val uIndex = row * strideUv + col
                        v.position(vIndex)
                        u.position(uIndex)
                        val vv = v.get()
                        val uu = u.get()
                        val out = ySize + row * width + col * 2
                        nv21[out] = vv
                        nv21[out + 1] = uu
                    }
                }

                val jpeg = ByteArrayOutputStream()
                YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
                    .compressToJpeg(Rect(0, 0, width, height), 88, jpeg)
                val bytes = jpeg.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

                val rotation = frame.rotation
                if (rotation == 0) {
                    bitmap
                } else {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        .also { if (it !== bitmap) bitmap.recycle() }
                }
            } finally {
                i420.release()
            }
        }.getOrNull()
    }

    fun decodeJpeg(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
