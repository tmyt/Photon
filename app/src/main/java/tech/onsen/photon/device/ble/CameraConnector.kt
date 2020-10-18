package tech.onsen.photon.device.ble

import android.bluetooth.*
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.onsen.photon.data.ConnectionInfo
import tech.onsen.photon.helpers.decodeToString
import tech.onsen.photon.helpers.dump
import tech.onsen.photon.helpers.encodeToByteArray
import java.util.*

class CameraConnector private constructor(private val adapter: BluetoothAdapter) : BluetoothGattCallback() {
    companion object {
        /** Supported MAX MTU **/
        private const val MaxMtu = 36
        /** BLE UUIDs **/
        private const val ServiceUuid = "0000EA90-0000-1000-8000-00805F9B34FB"
        private const val CharacteristicUuid = "00003F1F-0000-1000-8000-00805F9B34FB"
        private const val NotifyDescriptorUuid = "00002902-0000-1000-8000-00805f9b34fb"
    }

    class Builder(private val adapter: BluetoothAdapter) {
        private var _onStatusChanged: (String) -> Unit = {}
        private var _identifier: String = "000000"

        fun setOnStatusChanged(handler: (String) -> Unit): Builder {
            _onStatusChanged = handler
            return this
        }

        fun setIdentifier(identifier: String): Builder {
            _identifier = identifier
            return this
        }

        fun connect(device: BluetoothDevice): CompletableDeferred<ConnectionInfo> {
            val connector = CameraConnector(adapter)
            connector.onStatusUpdated = _onStatusChanged
            connector.identifier = _identifier
            return connector.connect(device)
        }
    }

    /** BLE **/
    private var _gatt: BluetoothGatt? = null
    private var _characteristics: BluetoothGattCharacteristic? = null
    /** Connection Info **/
    private var essid: String = ""
    private var passphrase: String = ""
    private var deferred: CompletableDeferred<ConnectionInfo> = CompletableDeferred()

    /** public properties **/
    var onStatusUpdated: (String) -> Unit = {}
    var identifier: String = ""

    fun connect(device: BluetoothDevice): CompletableDeferred<ConnectionInfo> {
        Log.i("BLE", "Connecting")
        device.connectGatt(null, false, this)
        return deferred
    }

    private fun disconnect() {
        _gatt?.disconnect()
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (gatt == null) return
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            onStatusUpdated("Requesting MTU")
            _gatt = gatt
            gatt.requestMtu(138)
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS) {
            onError()
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (gatt == null) return
        onStatusUpdated("Discovering services")
        gatt.discoverServices()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (gatt == null) return
        if (status != BluetoothGatt.GATT_SUCCESS) return
        onStatusUpdated("Discovering characteristics")
        _characteristics = getCharacteristics()
        enableNotification()
        sendPairRequest()
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        val value = characteristic?.value ?: return
        onReceive(value)
    }

    private fun getCharacteristics(): BluetoothGattCharacteristic? {
        val service = _gatt?.getService(UUID.fromString(ServiceUuid))
        return service?.getCharacteristic(UUID.fromString(CharacteristicUuid))
    }

    private fun enableNotification() {
        val gatt = this._gatt ?: return
        val characteristic = this._characteristics ?: return
        gatt.setCharacteristicNotification(_characteristics, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString(NotifyDescriptorUuid))
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        gatt.writeDescriptor(descriptor)
    }

    private fun writeValue(value: ByteArray) = GlobalScope.launch{
        writeValueSuspendable(value)
    }

    private suspend fun writeValueSuspendable(value: ByteArray) {
        delay(333)
        Log.i("BLE", ">>> ${value.dump()}");
        _characteristics?.value = value
        _gatt?.writeCharacteristic(_characteristics)
    }

    /** Hasselblad X1D II GATT Protocol Handler **/
    private fun sendPairRequest() {
        // Pair Request
        // 00 Length 01 30 32 30 42 43 43 <UTF8String>
        val name = adapter.name.encodeToByteArray()
        val identifier = identifier.encodeToByteArray()
        val length = Math.min(name.size, MaxMtu - 12)
        val value = ByteArray(9 + length)
        value[1] = (7 + length).toByte()
        value[2] = 0x01
        identifier.copyInto(value, 3)
        name.copyInto(value, 9)
        onStatusUpdated("Initiating connection");
        writeValue(value)
    }

    private fun requestConnectionInfo() {
        // Wifi Info Request
        // 01 01 01
        onStatusUpdated("Requesting WiFi information")
        writeValue(byteArrayOf(0x01, 0x01, 0x01))
    }

    private fun sendDisconnect(success: Boolean) = GlobalScope.launch {
        // Disconnect?
        // 02 01 01
        writeValue(byteArrayOf(0x02, 0x01, 0x01))
        delay(333)
        disconnect()
        when (success) {
            true -> deferred.complete(ConnectionInfo(essid, passphrase))
            false -> deferred.completeExceptionally(Exception())
        }
    }

    private fun onReceive(value: ByteArray) {
        /*
         * Packet Format
         * AA BB CC...
         *  ^  ^  ^- Payload
         *  |  +---- Payload Length
         *  +------- Response Type
         */
        Log.i("BLE", "<<< ${value.dump()}");
        when (value[0].toInt()) {
            0x0D -> {
                // Connection Denied
                // Payload = 0x00
                onStatusUpdated("Connection denied");
                sendDisconnect(false);
            }
            0x0A -> {
                // Connection Accepted
                // Payload = Bluetooth Aadapter MAC Address
                onStatusUpdated("Connection accepted");
                requestConnectionInfo();
            }
            0x0B -> {
                // ESSID
                // Payload = UTF8 String
                onStatusUpdated("SSID received");
                essid = value.decodeToString(2, value[1] + 2)
                CheckCompletion();
            }
            0x0C -> {
                // Passphrase
                // Payload = UTF8 String
                onStatusUpdated("Passphrase received");
                passphrase = value.decodeToString(2, value[1] + 2);
                CheckCompletion();
            }
            0x0E -> {
                // Request Authorization
                // Payload = N/A
                onStatusUpdated("Authorize access from Camera");
            }
        }
    }

    private fun onError() {
        onStatusUpdated("Connection error");
        sendDisconnect(false);
    }

    private fun CheckCompletion() {
        if (essid.isEmpty() || passphrase.isEmpty()) return
        onStatusUpdated("WiFi information received")
        sendDisconnect(true)
    }
}