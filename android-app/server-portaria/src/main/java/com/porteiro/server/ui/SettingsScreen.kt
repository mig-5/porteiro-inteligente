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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.porteiro.server.ServerViewModel
import com.porteiro.server.settings.ServerSettings

@Composable
fun SettingsScreen(
    viewModel: ServerViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    val faceState by viewModel.faceState.collectAsState()

    var port by remember(settings) { mutableStateOf(settings.serverPort.toString()) }
    var espIp by remember(settings) { mutableStateOf(settings.esp32Ip) }
    var espPort by remember(settings) { mutableStateOf(settings.esp32Port.toString()) }
    var secret by remember(settings) { mutableStateOf(settings.sharedSecret) }
    var duration by remember(settings) { mutableStateOf(settings.relayDurationMs.toString()) }
    var threshold by remember(settings) { mutableFloatStateOf(settings.similarityThreshold) }
    var autoUnlock by remember(settings) { mutableStateOf(settings.autoUnlockEnabled) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        SectionCard {
            Text("Servidor local", style = MaterialTheme.typography.titleLarge)
            Text(
                if (serverState.ip != null) "Endereço atual: ${serverState.ip}:${serverState.port}"
                else "Sem IP local. Conecte ao Wi-Fi da portaria.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Porta HTTP/WebSocket") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Ao salvar, o servidor é reiniciado na nova porta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard {
            Text("Módulo ESP32", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = espIp,
                onValueChange = { espIp = it },
                label = { Text("IP do ESP32") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = espPort,
                    onValueChange = { espPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Porta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text("Pulso (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("Chave compartilhada (X-App-Secret)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Deve ser idêntica a APP_SECRET no config.h do firmware.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard {
            Text("Reconhecimento facial", style = MaterialTheme.typography.titleLarge)
            Text(
                "Similaridade mínima: ${(threshold * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 0.5f..0.95f,
                steps = 8
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Liberação automática ao reconhecer")
                Switch(checked = autoUnlock, onCheckedChange = { autoUnlock = it })
            }
            if (!faceState.modelAvailable) {
                Text(
                    "Modelo ausente: adicione mobilefacenet.tflite em assets para ativar a liberação facial.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = {
                viewModel.saveSettings(
                    ServerSettings(
                        serverPort = port.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080,
                        esp32Ip = espIp.trim(),
                        esp32Port = espPort.toIntOrNull()?.coerceIn(1, 65535) ?: 80,
                        sharedSecret = secret,
                        similarityThreshold = threshold,
                        relayDurationMs = duration.toIntOrNull()?.coerceIn(100, 5000) ?: 1500,
                        autoUnlockEnabled = autoUnlock
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Salvar ajustes")
        }

        Spacer(Modifier.height(24.dp))
    }
}
