package com.porteiro.server

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.porteiro.server.service.PortariaService
import com.porteiro.server.ui.PorteiroTheme
import com.porteiro.server.ui.ServerApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PortariaApp
        app.controller.startNetwork()
        PortariaService.start(this)

        setContent {
            var permissionTick by remember { mutableIntStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) permissionTick++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionTick++ }

            val cameraGranted = remember(permissionTick) {
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
            val audioGranted = remember(permissionTick) {
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }

            LaunchedEffect(Unit) {
                val missing = mutableListOf<String>()
                if (!cameraGranted) missing.add(Manifest.permission.CAMERA)
                if (!audioGranted) missing.add(Manifest.permission.RECORD_AUDIO)
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
                if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray())
            }

            LaunchedEffect(cameraGranted) {
                if (cameraGranted) app.controller.startCameraPipeline()
            }

            PorteiroTheme {
                val serverViewModel: ServerViewModel = viewModel()
                ServerApp(
                    viewModel = serverViewModel,
                    cameraGranted = cameraGranted,
                    audioGranted = audioGranted
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (application as PortariaApp).controller.stopCameraPipeline()
        }
        super.onDestroy()
    }
}
