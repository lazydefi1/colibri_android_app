package com.example.colibriwallet

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import com.example.colibriwallet.ui.theme.ColibriWalletTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColibriWalletTheme {
                PermissionAwareApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionAwareApp(viewModel: MainViewModel) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            permissionsState.allPermissionsGranted -> {
                // All permissions granted, show main screen
                MainScreen(viewModel)
            }
            permissionsState.shouldShowRationale -> {
                // Show rationale and request permissions
                PermissionRationaleScreen(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
            else -> {
                // First time or permanently denied, show permission request
                PermissionRequestScreen(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Colibri Wallet",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "This app needs Bluetooth and Location permissions to connect to your hardware wallet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun PermissionRationaleScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Colibri Wallet requires the following permissions:\n\n" +
                    "• Bluetooth: To communicate with your hardware wallet\n" +
                    "• Location: Required by Android for Bluetooth scanning\n\n" +
                    "Your location data is not collected or stored.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}
