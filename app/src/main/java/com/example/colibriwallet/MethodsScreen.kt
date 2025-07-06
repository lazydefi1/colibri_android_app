package com.example.colibriwallet

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.colibriwallet.ble.BleDevice

@Composable
fun MethodsScreen(
    viewModel: MainViewModel,
    selectedDevice: BleDevice,
    rpcResponse: String,
    onNavigateBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val connectionState = viewModel.connectionState.collectAsState()
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
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "RPC Response",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = selectedDevice.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Address: ${selectedDevice.address}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Status: ${connectionState.value}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (connectionState.value) {
                        is com.example.colibriwallet.ble.ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                        is com.example.colibriwallet.ble.ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
                        is com.example.colibriwallet.ble.ConnectionState.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    messages = emptyList() // Clear previous logs
                    viewModel.sendListMethods()
                },
                enabled = connectionState.value is com.example.colibriwallet.ble.ConnectionState.Connected,
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(rpcResponse))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy JSON", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val logsText = messages.joinToString("\n") { "• $it" }
                    clipboardManager.setText(AnnotatedString(logsText))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy Logs", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content with Tabs-like sections
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "JSON Response",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Real-time Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Side by side content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // JSON Response Card
            Card(
                modifier = Modifier.weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (rpcResponse.isNotBlank()) {
                        Text(
                            text = formatJsonResponse(rpcResponse),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        )
                    } else {
                        Text(
                            text = if (connectionState.value is com.example.colibriwallet.ble.ConnectionState.Connected) {
                                "Sending request...\nWaiting for response..."
                            } else {
                                "Device not connected.\nConnect to device first."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            // Real-time Logs Card
            Card(
                modifier = Modifier.weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    reverseLayout = true // Show newest messages at bottom
                ) {
                    items(messages.takeLast(50).reversed()) { message -> // Show last 50 messages, newest at bottom
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (message.contains("error", ignoreCase = true) || message.contains("failed", ignoreCase = true)) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    } else if (message.contains("received", ignoreCase = true) || message.contains("complete", ignoreCase = true)) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Additional Actions
        if (connectionState.value is com.example.colibriwallet.ble.ConnectionState.Connected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Available Actions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                                                Button(
                            onClick = {
                                messages = emptyList() // Clear previous logs
                                viewModel.sendCustomRpc("""{"method":"getInfo"}""")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Get Info")
                        }

                        Button(
                            onClick = {
                                messages = emptyList() // Clear previous logs
                                viewModel.sendCustomRpc("""{"method":"getStatus"}""")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Get Status")
                        }
                    }
                }
            }
        }
    }
}

fun formatJsonResponse(jsonResponse: String): String {
    return try {
        // Simple JSON formatting - add proper indentation
        var formatted = jsonResponse
        var indent = 0
        val result = StringBuilder()
        var inString = false
        var escapeNext = false

        for (char in formatted) {
            when {
                escapeNext -> {
                    result.append(char)
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    result.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    result.append(char)
                    if (!escapeNext) inString = !inString
                }
                !inString && (char == '{' || char == '[') -> {
                    result.append(char)
                    indent++
                    result.append('\n')
                    result.append("  ".repeat(indent))
                }
                !inString && (char == '}' || char == ']') -> {
                    indent--
                    result.append('\n')
                    result.append("  ".repeat(indent))
                    result.append(char)
                }
                !inString && char == ',' -> {
                    result.append(char)
                    result.append('\n')
                    result.append("  ".repeat(indent))
                }
                !inString && char == ':' -> {
                    result.append(char)
                    result.append(' ')
                }
                char.isWhitespace() && !inString -> {
                    // Skip extra whitespace
                }
                else -> {
                    result.append(char)
                }
            }
        }

        result.toString()
    } catch (e: Exception) {
        // If formatting fails, return original
        jsonResponse
    }
}