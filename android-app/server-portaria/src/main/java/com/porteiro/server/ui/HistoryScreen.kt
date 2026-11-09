package com.porteiro.server.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.porteiro.server.data.AccessLog
import com.porteiro.server.data.ApartmentWithResidents

@Composable
fun HistoryScreen(
    logs: List<AccessLog>,
    apartments: List<ApartmentWithResidents>,
    modifier: Modifier = Modifier
) {
    val residentNames = apartments
        .flatMap { it.residents }
        .associate { it.resident.id to it.resident.name }

    Column(modifier.fillMaxWidth()) {
        Text(
            "Histórico de acessos",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (logs.isEmpty()) {
            EmptyHint("Nenhum evento registrado ainda.")
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val success = log.status == "SUCCESS"
                    SectionCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                caseLabel(log.triggerType),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            StatusPill(
                                text = statusLabel(log.status),
                                color = if (success) Color(0xFF1B8A5A) else Color(0xFFB3261E)
                            )
                        }
                        Text(
                            residentNames[log.residentId] ?: "Não identificado",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            formatLog(log),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
