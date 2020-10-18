package tech.onsen.photon.device.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.os.ParcelUuid

class CameraFinder(private val scanner: BluetoothLeScanner) : ScanCallback() {
    companion object {
        private const val ServiceUuid = "0000EA90-0000-1000-8000-00805F9B34FB"
        private const val CharacteristicUuid = "00003F1F-0000-1000-8000-00805F9B34FB"
        private const val NotifyDescriptorUuid = "00002902-0000-1000-8000-00805f9b34fb"
    }

    var onCameraFound: (device: BluetoothDevice) -> Unit = {}

    fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(ServiceUuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(listOf(filter), settings, this)
    }

    fun stopScan(){
        scanner.stopScan(this)
    }

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        val device = result?.device ?: return
        onCameraFound(device)
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
    }
}