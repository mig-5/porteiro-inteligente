package com.porteiro.server.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.porteiro.server.ServerViewModel
import com.porteiro.server.data.ApartmentWithResidents

@Composable
fun HomeScreen(
    viewModel: ServerViewModel,
    apartments: List<ApartmentWithResidents>,
    audioGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val serverState by viewModel.serverState.collectAsState()
    val faceState by viewModel.faceState.collectAsState()
    var number by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Terminal da portaria", style = MaterialTheme.typography.headlineMedium)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (serverState.running) "Servidor ativo" else "Servidor parado",
                    color = if (serverState.running) Color(0xFF1B8A5A) else Color(0xFFB3261E)
                )
            }
            Text(
                if (serverState.ip != null) "${serverState.ip}:${serverState.port}"
                else "Aguardando rede Wi-Fi local",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (serverState.cameraReady) "Câmera pronta" else "Câmera inativa",
                    color = if (serverState.cameraReady) Color(0xFF1B8A5A) else Color(0xFF8A6D1B)
                )
                StatusPill(
                    text = if (faceState.modelAvailable) "IA facial pronta" else "IA facial sem modelo",
                    color = if (faceState.modelAvailable) Color(0xFF1B8A5A) else Color(0xFF8A6D1B)
                )
            }
            serverState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            faceState.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (!audioGranted) {
                Text(
                    "Conceda permissão de microfone para atender chamadas.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        SectionCard {
            Text("Chamar apartamento", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = number,
                onValueChange = { value -> number = value.filter { it.isDigit() || it == '-' } },
                label = { Text("Número do apartamento") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val apartment = apartments.firstOrNull {
                        it.apartment.number.equals(number.trim(), ignoreCase = true)
                    }?.apartment
                    if (apartment == null) {
                        feedback = "Apartamento \"${number.trim()}\" não encontrado"
                    } else if (!audioGranted) {
                        feedback = "Permissão de microfone necessária"
                    } else {
                        feedback = null
                        viewModel.dial(apartment)
                    }
                },
                enabled = number.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Chamar", style = MaterialTheme.typography.labelLarge)
            }
            feedback?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Apartamentos", style = MaterialTheme.typography.titleLarge)
            FilledTonalButton(
                onClick = {
                    viewModel.manualUnlock { ok ->
                        feedback = if (ok) "Portão acionado" else "Falha ao acionar o portão"
                    }
                }
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.padding(3.dp))
                Text("Abrir portão")
            }
        }

        if (apartments.isEmpty()) {
            EmptyHint("Nenhum apartamento cadastrado. Use a aba Cadastros.")
        } else {
            apartments.forEach { item ->
                ApartmentDialCard(
                    item = item,
                    enabled = audioGranted,
                    onDial = { viewModel.dial(item.apartment) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ApartmentDialCard(
    item: ApartmentWithResidents,
    enabled: Boolean,
    onDial: () -> Unit
) {
    val residents = item.residents.joinToString { it.resident.name }
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(apartmentLabel(item.apartment), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (residents.isBlank()) "Sem moradores cadastrados" else residents,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onDial, enabled = enabled) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("Chamar")
            }
        }
    }
}

