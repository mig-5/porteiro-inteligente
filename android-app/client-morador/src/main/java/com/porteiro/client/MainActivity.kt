package com.porteiro.client

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.porteiro.client.service.MoradorService
import com.porteiro.client.ui.CallOverlay
import com.porteiro.client.ui.HomeScreen
import com.porteiro.client.ui.PairingScreen
import com.porteiro.client.ui.PorteiroTheme
import com.porteiro.client.webrtc.ClientCallPhase

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MoradorService.start(this)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            }

            val viewModel: MoradorViewModel = viewModel()
            val config by viewModel.config.collectAsState()
            val callState by viewModel.callState.collectAsState()

            PorteiroTheme {
                Box(Modifier.fillMaxSize()) {
                    val current = config
                    if (current == null) {
                        PairingScreen(viewModel)
                    } else {
                        HomeScreen(viewModel, current)
                    }
                    if (callState.phase != ClientCallPhase.IDLE) {
                        CallOverlay(viewModel, callState)
                    }
                }
            }
        }
    }
}
