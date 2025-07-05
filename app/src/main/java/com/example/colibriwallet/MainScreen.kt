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
import androidx.compose.ui.Modifier
import com.example.colibriwallet.ble.ConnectionState

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state = viewModel.connectionState.collectAsState()
    val messages = viewModel.messages.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "State: ${'$'}{state.value}")
        LazyColumn {
            items(messages.value) { msg ->
                Text(text = msg)
            }
        }
        Button(onClick = { viewModel.connectAndSendListMethods() }) {
            Text("Retry")
        }
    }
}
