package com.porteiro.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.porteiro.server.PortariaController
import com.porteiro.server.data.AccessLog
import com.porteiro.server.data.Apartment
import com.porteiro.server.data.TriggerType
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraPreview(
    controller: PortariaController,
    modifier: Modifier = Modifier,
    mirror: Boolean = true
) {
    val eglBase = remember { com.porteiro.server.webrtc.WebRtcCore.newRendererEglBase() }
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                controller.attachPreview(this)
            }
        },
        modifier = modifier,
        onRelease = { renderer ->
            controller.detachPreview(renderer)
            renderer.release()
            eglBase.release()
        }
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

fun apartmentLabel(apartment: Apartment): String =
    if (apartment.block.isNullOrBlank()) "Apt. ${apartment.number}"
    else "${apartment.block} - ${apartment.number}"

fun caseLabel(value: String): String = when (value) {
    TriggerType.FACIAL_RECOGNITION -> "Reconhecimento facial"
    TriggerType.APP_REMOTE -> "Aplicativo do morador"
    TriggerType.MANUAL_PORTARIA -> "Portaria (manual)"
    else -> value
}

fun statusLabel(value: String): String = when (value) {
    "SUCCESS" -> "Sucesso"
    "FAILED_UNAUTHORIZED" -> "Não autorizado"
    "FAILED_FACE_NOT_MATCH" -> "Rosto não reconhecido"
    "FAILED_GATEWAY" -> "Falha no portão"
    else -> value
}

private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

fun formatLog(log: AccessLog): String = dateFormat.format(Date(log.timestamp))

@Composable
fun EmptyHint(text: String, padding: PaddingValues = PaddingValues(16.dp)) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
