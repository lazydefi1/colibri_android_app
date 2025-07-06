package com.example.colibriwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.colibriwallet.ble.BleDevice
import com.example.colibriwallet.ble.ConnectionState

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val connectionState = viewModel.connectionState.collectAsState()
    val discoveredDevices = viewModel.discoveredDevices.collectAsState()
    val bondedDevices = viewModel.bondedDevices.collectAsState()
    val isScanning = viewModel.isScanning.collectAsState()
    var messages by remember { mutableStateOf<List<String>>(emptyList()) }

    // Collect messages from the Flow and add them to the list
    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { message ->
            messages = messages + message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Colibri Wallet - BLE Scanner",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Connection Status
        Text(
            text = "Connection: ${connectionState.value}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.startDeviceDiscovery() },
                enabled = !isScanning.value
            ) {
                Text("Scan for Devices")
            }

            Button(
                onClick = { viewModel.getBondedDevices() }
            ) {
                Text("Show Bonded")
            }

            if (isScanning.value) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                Text("Scanning...", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bonded Devices Section
            if (bondedDevices.value.isNotEmpty()) {
                item {
                    Text(
                        text = "Bonded Devices (${bondedDevices.value.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(bondedDevices.value) { device ->
                    BleDeviceCard(
                        device = device,
                        isBonded = true
                    ) {
                        // TODO: Add connect functionality for bonded devices
                        viewModel.connectAndSendListMethods()
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Discovered Devices Section
            item {
                Text(
                    text = "Discovered Devices (${discoveredDevices.value.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(discoveredDevices.value) { device ->
                BleDeviceCard(
                    device = device,
                    isBonded = false
                ) {
                    // TODO: Add connect functionality
                    viewModel.connectAndSendListMethods()
                }
            }
        }

        // Messages Section
        if (messages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Messages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.height(120.dp)
            ) {
                items(messages.takeLast(5)) { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BleDeviceCard(
    device: BleDevice,
    isBonded: Boolean = false,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = device.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isBonded) {
                            Text(
                                text = "BONDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = "••${device.shortAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isBonded) {
                        Text(
                            text = "${device.rssi} dBm (${device.signalStrength})",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (device.signalStrength) {
                                "Excellent", "Good" -> MaterialTheme.colorScheme.primary
                                "Fair" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        Text(
                            text = "Bonded device (RSSI unknown)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (device.serviceUuids.isNotEmpty()) {
                        Text(
                            text = "${device.serviceUuids.size} service${if (device.serviceUuids.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (device.knownServiceNames.isNotEmpty()) {
                        Text(
                            text = "• ${device.knownServiceNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = onConnect,
                    enabled = device.isConnectable
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
