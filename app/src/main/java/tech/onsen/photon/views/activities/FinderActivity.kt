package tech.onsen.photon.views.activities

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_finder.*
import kotlinx.android.synthetic.main.item_device.view.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.onsen.photon.R
import tech.onsen.photon.data.ConnectionInfo
import tech.onsen.photon.device.ble.CameraConnector
import tech.onsen.photon.device.ble.CameraFinder
import tech.onsen.photon.device.wifi.WifiConfig
import tech.onsen.photon.net.DownloadManager
import tech.onsen.photon.views.recyclerview.ArrayAdapter
import java.util.*

class FinderActivity : AppCompatActivity() {
    private class DeviceListAdapter : ArrayAdapter<BluetoothDevice>() {
        var onSelect: (BluetoothDevice) -> Unit = {}

        override fun getLayoutId(viewType: Int) = R.layout.item_device

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val itemView = holder.itemView
            val data = this[position]
            itemView.text_name.text = data.name
            itemView.text_address.text = data.address
            itemView.setOnClickListener { onSelect(data) }
        }
    }

    private lateinit var bluetoothManager: BluetoothManager

    private var _timer = Timer()
    private var _viewAdapter = DeviceListAdapter()
    private var _recyclerView: RecyclerView? = null
    private var _finder: CameraFinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finder)
        setSupportActionBar(toolbar)
        bluetoothManager = ContextCompat.getSystemService(this, BluetoothManager::class.java)!!
        initializeRecyclerView()
        initializeFinder()

        // request permissions
        checkAndRequestPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onResume() {
        super.onResume()
        _timer = Timer()
        _timer.schedule(object : TimerTask() {
            override fun run() {
                checkServerAvailable()
            }
        }, 5 * 1000, 30 * 1000)
        val finder = _finder ?: return
        finder.startScan()
        title = "Searching nearby camera..."
    }

    override fun onPause() {
        super.onPause()
        _timer.cancel()
        val finder = _finder ?: return
        finder.stopScan()
    }

    private fun initializeRecyclerView() {
        val viewManager = LinearLayoutManager(this)
        _recyclerView = items.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = _viewAdapter
        }
        _viewAdapter.onSelect = { onSelectDevice(it) }
    }

    private fun initializeFinder() {
        val adapter = bluetoothManager.adapter
        val scanner = adapter.bluetoothLeScanner
        _finder = CameraFinder(scanner).also {
            it.onCameraFound = { onCameraFound(it) }
        }
    }

    private fun onCameraFound(device: BluetoothDevice) {
        Log.i("Photon", "${device.name}, ${device.address}")
        val exists = _viewAdapter.items.firstOrNull { it.address == device.address }
        if (exists != null) return
        _viewAdapter.add(device)
    }

    private fun onSelectDevice(device: BluetoothDevice) = GlobalScope.launch {
        val finder = _finder ?: return@launch
        finder.stopScan()
        try {
            val info = getConnectionInfo(device)
            val connected = connectWifiNetwork(info)
            if (connected) onConnected()
        } catch (e: Exception) {
        }
    }

    private fun onConnected() {
        val intent = Intent(this, GalleryActivity::class.java)
        startActivity(intent)
    }

    private fun onStatusUpdated(message: String) {
        val bar = supportActionBar
        lifecycleScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) {
            title = message
        }
    }

    private suspend fun getConnectionInfo(device: BluetoothDevice): ConnectionInfo {
        val adapter = bluetoothManager.adapter
        val builder = CameraConnector.Builder(adapter)
            .setIdentifier(getDeviceId())
            .setOnStatusChanged {
                Log.i("BLE", it)
                onStatusUpdated(it)
            }
        return builder.connect(device).await()
    }

    private suspend fun connectWifiNetwork(info: ConnectionInfo): Boolean {
        val wifi = WifiConfig(this)
        wifi.onStatusUpdated = {
            Log.i("WIFI", it)
            onStatusUpdated(it)
        }
        return wifi.connect(info.essid, info.passphrase)
    }

    private fun getDeviceId(): String {
        val androidId =
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        return androidId.substring(0, 6).padStart(6, '0')
    }

    private fun checkServerAvailable() = GlobalScope.run {
        Log.i("XXX", "checking")
        if (DownloadManager.checkServerAvailable()) {
            _finder?.stopScan()
            onConnected()
        }
    }

    private fun checkAndRequestPermissions(vararg permissions: String): Boolean {
        val requiredPermissions = permissions
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (requiredPermissions.isEmpty()) return true
        requestPermissions(requiredPermissions.toTypedArray(), REQUEST_PERMISSIONS)
        return false
    }

    companion object {
        private val REQUEST_PERMISSIONS = 1
    }
}