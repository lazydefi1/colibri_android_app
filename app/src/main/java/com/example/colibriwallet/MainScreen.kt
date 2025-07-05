package com.example.colibriwallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.colibriwallet.ble.ConnectionState

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state = viewModel.connectionState.collectAsState()
    var messages by remember { mutableStateOf<List<String>>(emptyList()) }

    // Collect messages from the Flow and add them to the list
    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { message ->
            messages = messages + message
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "State: ${state.value}")
        LazyColumn {
            items(messages) { msg ->
                Text(text = msg)
            }
        }
        Button(onClick = { viewModel.connectAndSendListMethods() }) {
            Text("Retry")
        }
    }
}
