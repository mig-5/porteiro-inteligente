package com.porteiro.server.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.porteiro.server.PairingUi
import com.porteiro.server.ServerViewModel
import com.porteiro.server.data.Apartment
import com.porteiro.server.data.ApartmentWithResidents
import com.porteiro.server.data.Resident
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun AdminScreen(
    viewModel: ServerViewModel,
    apartments: List<ApartmentWithResidents>,
    modifier: Modifier = Modifier
) {
    var showAddApartment by remember { mutableStateOf(false) }
    var addResidentFor by remember { mutableStateOf<Apartment?>(null) }
    var pairingFor by remember { mutableStateOf<Resident?>(null) }
    var deleteApartment by remember { mutableStateOf<Apartment?>(null) }
    var deleteResident by remember { mutableStateOf<Resident?>(null) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cadastros", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { showAddApartment = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("Apartamento")
            }
        }

        if (apartments.isEmpty()) {
            EmptyHint("Nenhuma unidade cadastrada.")
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apartments, key = { it.apartment.id }) { item ->
                    SectionCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                apartmentLabel(item.apartment),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Row {
                                IconButton(onClick = { addResidentFor = item.apartment }) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = "Adicionar morador")
                                }
                                IconButton(onClick = { deleteApartment = item.apartment }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir apartamento")
                                }
                            }
                        }
                        if (item.residents.isEmpty()) {
                            Text(
                                "Sem moradores",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.residents.forEachIndexed { index, residentWithDevices ->
                            if (index > 0) HorizontalDivider()
                            val resident = residentWithDevices.resident
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(resident.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        buildString {
                                            append(resident.phone.ifBlank { "sem telefone" })
                                            append(" • ")
                                            append("${residentWithDevices.embeddings.size} face(s)")
                                            append(" • ")
                                            append("${residentWithDevices.devices.count { it.pairedAt != null }} aparelho(s)")
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    FilledTonalButton(onClick = { pairingFor = resident }) {
                                        Icon(Icons.Filled.QrCode2, contentDescription = null)
                                        Spacer(Modifier.padding(2.dp))
                                        Text("QR")
                                    }
                                    IconButton(onClick = { deleteResident = resident }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Excluir morador")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddApartment) {
        AddApartmentDialog(
            onDismiss = { showAddApartment = false },
            onConfirm = { number, block ->
                viewModel.addApartment(number, block)
                showAddApartment = false
            }
        )
    }

    addResidentFor?.let { apartment ->
        AddResidentDialog(
            apartment = apartment,
            onDismiss = { addResidentFor = null },
            onConfirm = { name, phone, admin ->
                viewModel.addResident(apartment.id, name, phone, admin)
                addResidentFor = null
            }
        )
    }

    pairingFor?.let { resident ->
        PairingDialog(
            viewModel = viewModel,
            resident = resident,
            onDismiss = { pairingFor = null }
        )
    }

    deleteApartment?.let { apartment ->
        ConfirmDeleteDialog(
            title = "Excluir ${apartmentLabel(apartment)}?",
            message = "Moradores, aparelhos, biometrias e vínculos serão removidos.",
            onDismiss = { deleteApartment = null },
            onConfirm = {
                viewModel.deleteApartment(apartment)
                deleteApartment = null
            }
        )
    }

    deleteResident?.let { resident ->
        ConfirmDeleteDialog(
            title = "Excluir ${resident.name}?",
            message = "Aparelhos pareados e biometrias deste morador serão removidos.",
            onDismiss = { deleteResident = null },
            onConfirm = {
                viewModel.deleteResident(resident)
                deleteResident = null
            }
        )
    }
}

@Composable
private fun AddApartmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var block by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo apartamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Número / identificador") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = block,
                    onValueChange = { block = it },
                    label = { Text("Bloco / torre (opcional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(number.trim(), block.trim().ifBlank { null }) },
                enabled = number.isNotBlank()
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddResidentDialog(
    apartment: Apartment,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Morador — ${apartmentLabel(apartment)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome completo") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+-() " } },
                    label = { Text("Telefone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privilégios administrativos")
                    Switch(checked = admin, onCheckedChange = { admin = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim(), phone.trim(), admin) }, enabled = name.isNotBlank()) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PairingDialog(
    viewModel: ServerViewModel,
    resident: Resident,
    onDismiss: () -> Unit
) {
    var deviceName by remember { mutableStateOf("Celular de ${resident.name}") }
    var pairing by remember { mutableStateOf<PairingUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(pairing) {
        while (pairing != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = {
            pairing?.let { viewModel.cancelPairing(it.payload) }
            onDismiss()
        },
        title = { Text("Parear morador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(resident.name, style = MaterialTheme.typography.titleLarge)
                if (pairing == null) {
                    Text("Gere o QR Code e leia com o aplicativo do morador.")
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Nome do aparelho") },
                        singleLine = true
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    val current = pairing!!
                    Image(
                        bitmap = current.bitmap.asImageBitmap(),
                        contentDescription = "QR Code de pareamento",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    Text("${current.payload.serverIp}:${current.payload.port}")
                    val remaining = ((current.expiresAt - now) / 1000).coerceAtLeast(0)
                    Text(
                        if (remaining > 0) "Expira em ${remaining}s" else "QR Code expirado",
                        color = if (remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Token: ${current.payload.authToken}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (pairing == null) {
                Button(
                    onClick = {
                        error = null
                        viewModel.startPairing(
                            residentId = resident.id,
                            deviceName = deviceName,
                            onReady = { pairing = it },
                            onError = { error = it }
                        )
                    }
                ) { Text("Gerar QR Code") }
            } else {
                TextButton(onClick = {
                    pairing?.let { viewModel.cancelPairing(it.payload) }
                    onDismiss()
                }) { Text("Fechar") }
            }
        },
        dismissButton = {
            if (pairing == null) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
