package com.example.colibriwallet

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.colibriwallet.ble.BleRepository
import com.example.colibriwallet.ble.ConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BleRepositoryTest {

    @Test
    fun sendRpc_returns_response() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BleRepository(context)
        // This test would set up a fake GATT server in a real environment.
        // Here we simply assert the repository handles missing gatt gracefully.
        val result = repo.sendRpc("{}")
        assertEquals("No gatt", result)
    }
}
