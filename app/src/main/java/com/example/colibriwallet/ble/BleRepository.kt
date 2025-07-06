package com.example.colibriwallet.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.charset.Charset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val SERVICE_UUID = UUID.fromString("31415926-5358-9793-2384-626433832795")

@Singleton
class BleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val adapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = flow { for (m in _messages) emit(m) }

    // Device discovery
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices

    private val _bondedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BleDevice>> = _bondedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun getBondedDevices() {
        try {
            val bondedDevices = adapter.bondedDevices
            val bleDevices = bondedDevices
                .filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
                .map { device ->
                    BleDevice(
                        device = device,
                        name = device.name ?: "Bonded Device",
                        address = device.address,
                        rssi = -50, // Unknown RSSI for bonded devices
                        serviceUuids = emptyList(), // Can't get services without scanning
                        isConnectable = true
                    )
                }
            _bondedDevices.value = bleDevices
            _messages.trySend("Found ${bleDevices.size} bonded BLE devices")
        } catch (e: Exception) {
            _messages.trySend("Error getting bonded devices: ${e.message}")
        }
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    suspend fun startDeviceDiscovery() {
        if (_isScanning.value) return

        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        _messages.send("Starting BLE device scan...")

        val scanner = adapter.bluetoothLeScanner
        val discoveredDevicesMap = mutableMapOf<String, BleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val scanRecord = result.scanRecord
                val serviceUuids = scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()

                                // Try to get device name from multiple sources
                val deviceName = device.name
                    ?: scanRecord?.deviceName
                    ?: getManufacturerName(scanRecord)
                    ?: "Unknown Device"

                // Debug: Log scan record info
                _messages.trySend("Device: ${device.address} | Name: ${device.name} | ScanRecord Name: ${scanRecord?.deviceName} | Manufacturer: ${getManufacturerName(scanRecord)}")

                val bleDevice = BleDevice(
                    device = device,
                    name = deviceName,
                    address = device.address,
                    rssi = result.rssi,
                    serviceUuids = serviceUuids,
                    isConnectable = result.isConnectable
                )

                // Update or add device (use address as key to avoid duplicates)
                discoveredDevicesMap[device.address] = bleDevice
                _discoveredDevices.value = discoveredDevicesMap.values.sortedByDescending { it.rssi }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
                _messages.trySend("Scan failed with error: $errorCode")
            }
        }

        try {
            scanner.startScan(callback)

            // Scan for 10 seconds
            kotlinx.coroutines.delay(10000)

            scanner.stopScan(callback)
            _isScanning.value = false
            _messages.send("Scan completed. Found ${discoveredDevicesMap.size} devices.")

        } catch (e: Exception) {
            scanner.stopScan(callback)
            _isScanning.value = false
            _messages.send("Scan error: ${e.message}")
        }
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    suspend fun connectAndSendListMethods() {
        if (!_connect()) return
        val response = sendRpc("""{"method":"listMethods"}""")
        _messages.send(response)
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    private suspend fun _connect(): Boolean {
        if (gatt != null) return true
        _connectionState.value = ConnectionState.Connecting
        val scanner = adapter.bluetoothLeScanner
        var device: BluetoothDevice? = null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true) {
                    device = result.device
                }
            }
        }
        scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()),
            ScanSettings.Builder().build(), callback)
        withTimeout(15000) {
            while (device == null) kotlinx.coroutines.delay(500)
        }
        scanner.stopScan(callback)
        val dev = device ?: return false.also {
            _connectionState.value = ConnectionState.Failed("Device not found")
        }
        return suspendCancellableCoroutine { cont ->
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dev.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            _connectionState.value = ConnectionState.Failed("Disconnected")
                            cont.resume(false) {}
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        for (service in gatt.services) {
                            if (service.uuid == SERVICE_UUID) {
                                for (ch in service.characteristics) {
                                    when (ch.uuid) {
                                        shortUuidTo128(0xC001) -> writeChar = ch
                                        shortUuidTo128(0xC000) -> {
                                            notifyChar = ch
                                            gatt.setCharacteristicNotification(ch, true)
                                        }
                                    }
                                }
                            }
                        }
                        if (writeChar != null && notifyChar != null) {
                            _connectionState.value = ConnectionState.Connected
                            cont.resume(true) {}
                        } else {
                            _connectionState.value = ConnectionState.Failed("Chars missing")
                            cont.resume(false) {}
                        }
                    }
                }, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M)
            } else {
                dev.connectGatt(context, false, object : BluetoothGattCallback() {})
            }
        }
    }

    suspend fun sendRpc(json: String): String {
        val char = writeChar ?: return "No write char"
        val g = gatt ?: return "No gatt"
        val data = json.toByteArray(Charset.forName("UTF-8"))
        char.value = data
        return suspendCancellableCoroutine { cont ->
            val cb = object : BluetoothGattCallback() {
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    val value = characteristic.value.toString(Charsets.UTF_8)
                    cont.resume(value) {}
                }
            }
            g.setCharacteristicNotification(notifyChar, true)
            g.writeCharacteristic(char)
        }
    }

    private fun getManufacturerName(scanRecord: ScanRecord?): String? {
        scanRecord?.manufacturerSpecificData?.let { manufacturerData ->
            // Check if the SparseArray has any elements before accessing
            if (manufacturerData.size() > 0) {
                val companyId = manufacturerData.keyAt(0)
                return when (companyId) {
                    0x004C -> "Apple"
                    0x0006 -> "Microsoft"
                    0x00E0 -> "Google"
                    0x0075 -> "Samsung"
                    0x000F -> "Broadcom"
                    0x0087 -> "Garmin"
                    0x01D7 -> "Qualcomm"
                    0x02E5 -> "Espressif" // ESP32 manufacturer
                    else -> "Manufacturer ID: 0x${companyId.toString(16).uppercase()}"
                }
            }
        }
        return null
    }

    fun close() {
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
