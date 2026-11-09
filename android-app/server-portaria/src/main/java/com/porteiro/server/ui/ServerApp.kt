package com.porteiro.server.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.porteiro.server.ServerViewModel
import com.porteiro.server.webrtc.CallStatus

private enum class ServerTab(val label: String) {
    HOME("Portaria"),
    ADMIN("Cadastros"),
    HISTORY("Histórico"),
    SETTINGS("Ajustes")
}

@Composable
fun ServerApp(
    viewModel: ServerViewModel,
    cameraGranted: Boolean,
    audioGranted: Boolean
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val apartments by viewModel.apartments.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val callState by viewModel.callState.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    ServerTab.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                Icon(
                                    when (item) {
                                        ServerTab.HOME -> Icons.Filled.Home
                                        ServerTab.ADMIN -> Icons.Filled.People
                                        ServerTab.HISTORY -> Icons.Filled.History
                                        ServerTab.SETTINGS -> Icons.Filled.Settings
                                    },
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (ServerTab.entries[tab]) {
                    ServerTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        apartments = apartments,
                        audioGranted = audioGranted && cameraGranted
                    )

                    ServerTab.ADMIN -> AdminScreen(viewModel, apartments)
                    ServerTab.HISTORY -> HistoryScreen(logs, apartments)
                    ServerTab.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }

        if (callState.status != CallStatus.IDLE) {
            CallOverlay(viewModel, callState)
        }
    }
}
