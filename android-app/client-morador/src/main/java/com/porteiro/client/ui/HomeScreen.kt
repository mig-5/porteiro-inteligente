package com.porteiro.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.porteiro.client.ConnectionStatus
import com.porteiro.client.MoradorViewModel
import com.porteiro.client.data.PairingConfig

@Composable
fun HomeScreen(viewModel: MoradorViewModel, config: PairingConfig) {
    val state by viewModel.state.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) viewModel.enrollFace(bitmap)
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Minha portaria", style = MaterialTheme.typography.headlineMedium)
            StatusPill(
                text = when (state.status) {
                    ConnectionStatus.CONNECTED -> "Conectado"
                    ConnectionStatus.CONNECTING -> "Conectando"
                    ConnectionStatus.ERROR -> "Sem conexão"
                    ConnectionStatus.DISCONNECTED -> "Desconectado"
                },
                color = when (state.status) {
                    ConnectionStatus.CONNECTED -> Color(0xFF1B8A5A)
                    ConnectionStatus.CONNECTING -> Color(0xFF8A6D1B)
                    else -> Color(0xFFB3261E)
                }
            )
        }

        SectionCard {
            Text(
                state.apartment ?: "Apartamento não identificado",
                style = MaterialTheme.typography.titleLarge
            )
            state.residentName?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "${config.serverIp}:${config.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.unlockMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(
            onClick = { viewModel.unlock() },
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B8A5A))
        ) {
            Icon(Icons.Filled.LockOpen, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("ABRIR PORTÃO", style = MaterialTheme.typography.titleLarge)
        }

        SectionCard {
            Text("Reconhecimento facial", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tire uma foto do seu rosto para liberar a entrada automaticamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.padding(3.dp))
                Text("Cadastrar meu rosto")
            }
            state.enrollmentMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.reconnect() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("Reconectar")
            }
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Desconectar")
            }
        }

        TextButton(onClick = { viewModel.clearUnlockMessage() }) {
            Text("Limpar avisos")
        }
    }
}
