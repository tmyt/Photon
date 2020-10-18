package tech.onsen.photon.device.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class WifiConfig(private val context: Context) {
    private val _manager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var onStatusUpdated: (String) -> Unit = {}

    suspend fun connect(essid: String, passphrase: String): Boolean {
        onStatusUpdated("Enabling WiFi")
        enableWifi()
        onStatusUpdated("Connecting to WiFi")
        // create configuration parameter
        val conf = WifiConfiguration().apply {
            SSID = "\"${essid}\""
            preSharedKey = "\"${passphrase}\""
            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
            allowedProtocols.set(WifiConfiguration.Protocol.RSN)
            allowedProtocols.set(WifiConfiguration.Protocol.WPA)
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
            allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
            allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
        }
        _manager.addNetwork(conf)
        _manager.saveConfiguration()
        _manager.updateNetwork(conf)
        // try connect
        val network = _manager.configuredNetworks.first { it.SSID == conf.SSID }
        _manager.disconnect()
        delay(333)
        _manager.enableNetwork(network.networkId, true)
        // wait for connection
        onStatusUpdated("Waiting for connection")
        waitForWifiEstablished(conf.SSID)
        return true
    }

    fun isConnected(): Boolean {
        val info = _manager.connectionInfo
        return _manager.isWifiEnabled && isHasselbladNetwork(info?.ipAddress ?: 0)
    }

    private suspend fun enableWifi(): CompletableDeferred<Boolean> {
        val source = CompletableDeferred<Boolean>()
        if (!_manager.isWifiEnabled) {
            _manager.setWifiEnabled(true)
            waitForWifiEnabled()
        } else {
            source.complete(true)
        }
        return source
    }

    private suspend fun waitForWifiEnabled() {
        while (_manager.isWifiEnabled) {
            delay(333)
        }
    }

    private suspend fun waitForWifiEstablished(essid: String) {
        while (true) {
            val info = _manager.connectionInfo
            if (info != null && info.ssid == essid && isHasselbladNetwork(info.ipAddress)) {
                break
            }
            delay(333)
        }
    }

    private fun isHasselbladNetwork(ipv4Address: Int): Boolean {
        return ipv4Address and 0x00ffffff == 0x0002A8C0
    }
}