package com.porteiro.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.porteiro.client.MoradorViewModel
import com.porteiro.client.data.PairingPayload

@Composable
fun PairingScreen(viewModel: MoradorViewModel) {
    var error by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            error = "Leitura cancelada"
        } else {
            val payload = PairingPayload.fromRaw(contents)
            if (payload == null) {
                error = "QR Code inválido. Gere um novo na portaria."
            } else {
                viewModel.savePairing(payload)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Porteiro Inteligente", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Para começar, escaneie o QR Code exibido no celular da portaria.",
            style = MaterialTheme.typography.bodyLarge
        )

        Button(
            onClick = {
                error = null
                scanLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Aponte para o QR Code da portaria")
                        setBeepEnabled(false)
                        setOrientationLocked(true)
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Escanear QR Code")
        }

        TextButton(onClick = { showManual = true }) {
            Text("Inserir dados manualmente")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Text(
            "O pareamento expira em 5 minutos. Se expirar, peça ao síndico para gerar um novo código.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showManual) {
        ManualPairingDialog(
            onDismiss = { showManual = false },
            onConfirm = { ip, port, token, deviceId ->
                viewModel.saveManual(ip, port, token, deviceId)
                showManual = false
            }
        )
    }
}

@Composable
private fun ManualPairingDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String, String) -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var token by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pareamento manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP da portaria") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Porta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token de autenticação") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("ID do dispositivo da portaria") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        ip.trim(),
                        port.toIntOrNull() ?: 8080,
                        token.trim(),
                        deviceId.trim()
                    )
                },
                enabled = ip.isNotBlank() && token.isNotBlank()
            ) { Text("Conectar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
