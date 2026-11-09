package com.porteiro.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.porteiro.server.ServerViewModel
import com.porteiro.server.webrtc.CallState
import com.porteiro.server.webrtc.CallStatus

@Composable
fun CallOverlay(viewModel: ServerViewModel, callState: CallState) {
    when (callState.status) {
        CallStatus.RINGING, CallStatus.CONNECTED -> ActiveCallPanel(viewModel, callState)
        CallStatus.FAILED, CallStatus.ENDED -> AlertDialog(
            onDismissRequest = { viewModel.resetCall() },
            title = { Text("Chamada") },
            text = { Text(callState.message ?: "Chamada encerrada") },
            confirmButton = { Button(onClick = { viewModel.resetCall() }) { Text("OK") } }
        )

        CallStatus.IDLE -> Unit
    }
}

@Composable
private fun ActiveCallPanel(viewModel: ServerViewModel, callState: CallState) {
    var unlockMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07110C)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                CameraPreview(
                    controller = viewModel.controller,
                    modifier = Modifier.fillMaxSize()
                )
                StatusPill(
                    text = if (callState.status == CallStatus.CONNECTED) "Em chamada" else "Chamando...",
                    color = if (callState.status == CallStatus.CONNECTED) Color(0xFF63D69F)
                    else Color(0xFFF2A900),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1B14))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    callState.apartmentLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                if (callState.residentNames.isNotEmpty()) {
                    Text(
                        callState.residentNames.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFC7D6CC)
                    )
                }
                callState.remoteDeviceName?.let {
                    Text(
                        "Atendendo em: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9FB3A6)
                    )
                }
                unlockMessage?.let {
                    Text(it, color = Color(0xFFF2A900), style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            unlockMessage = null
                            viewModel.hangUp()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Encerrar")
                    }
                    Button(
                        onClick = {
                            viewModel.manualUnlock { ok ->
                                unlockMessage = if (ok) "Portão acionado" else "Falha ao acionar o portão"
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B8A5A))
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Abrir portão")
                    }
                }
            }
        }
    }
}
