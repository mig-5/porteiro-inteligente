package com.porteiro.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneInTalk
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
import com.porteiro.client.MoradorViewModel
import com.porteiro.client.webrtc.ClientCallPhase
import com.porteiro.client.webrtc.ClientCallState

@Composable
fun CallOverlay(viewModel: MoradorViewModel, callState: ClientCallState) {
    when (callState.phase) {
        ClientCallPhase.RINGING -> RingingPanel(viewModel, callState)
        ClientCallPhase.CONNECTING, ClientCallPhase.CONNECTED -> ActivePanel(viewModel, callState)
        ClientCallPhase.ENDED, ClientCallPhase.FAILED -> AlertDialog(
            onDismissRequest = { viewModel.dismissCall() },
            title = { Text("Chamada da portaria") },
            text = { Text(callState.message ?: "Chamada encerrada") },
            confirmButton = { Button(onClick = { viewModel.dismissCall() }) { Text("OK") } }
        )

        ClientCallPhase.IDLE -> Unit
    }
}

@Composable
private fun RingingPanel(viewModel: MoradorViewModel, callState: ClientCallState) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF07110C)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.PhoneInTalk,
                contentDescription = null,
                tint = Color(0xFF63D69F),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Chamada da portaria",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                callState.apartment,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFC7D6CC)
            )
            if (callState.residents.isNotEmpty()) {
                Text(
                    callState.residents.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF9FB3A6)
                )
            }
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.declineCall() },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("Recusar")
                }
                Button(
                    onClick = { viewModel.acceptCall() },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B8A5A))
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("Atender")
                }
            }
        }
    }
}

@Composable
private fun ActivePanel(viewModel: MoradorViewModel, callState: ClientCallState) {
    var unlockMessage by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF07110C)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                if (callState.phase == ClientCallPhase.CONNECTED) {
                    RemoteVideo(
                        callManager = viewModel.callManager,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Conectando vídeo...", color = Color(0xFFC7D6CC))
                    }
                }
                StatusPill(
                    text = if (callState.phase == ClientCallPhase.CONNECTED) "Em chamada" else "Conectando",
                    color = if (callState.phase == ClientCallPhase.CONNECTED) Color(0xFF63D69F)
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
                    callState.apartment,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    "Visitante na portaria",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFC7D6CC)
                )
                unlockMessage?.let {
                    Text(it, color = Color(0xFFF2A900), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.hangUp() },
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
                            viewModel.unlock { _, message -> unlockMessage = message }
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
