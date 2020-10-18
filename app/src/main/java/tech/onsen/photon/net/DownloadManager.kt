package tech.onsen.photon.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.onsen.photon.helpers.decodeToString
import tech.onsen.photon.helpers.dump
import tech.onsen.photon.helpers.int
import tech.onsen.photon.helpers.short
import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.imaging.TinyTiff
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDateTime
import java.util.*

class DownloadManager {
    companion object {
        const val DEBUG = false
        private val MaxConcurrentJobs = 3

        fun checkServerAvailable(server: String = "192.168.2.1", port: Int = 9003): Boolean {
            val client = Socket()
            try {
                client.connect(InetSocketAddress(server, port))
                val stream = client.getInputStream()
                val header = ByteArray(8)
                stream.read(header)
                if (header[0] == 0x55.toByte() && header[1] == 0xCC.toByte()) return true
                return false
            } catch (e: Exception) {
                return false
            } finally {
                client.close()
            }
        }
    }

    enum class ConnectionStatus {
        Starting,
        Started,
        Stopping,
        Stopped,
    }

    private val _downloadSession: MutableMap<Short, Download> = mutableMapOf()
    private val _downloadQueue: Queue<Download> = LinkedList()
    private val _handler: Handler = Handler(Looper.getMainLooper())
    private var _client: Socket? = null
    private var _receiveThread: Thread? = null
    private var _counter: Short = 0

    var started: DownloadManager.() -> Unit = {}
    var stopped: DownloadManager.() -> Unit = {}
    var error: DownloadManager.(Exception) -> Unit = { }
    var newFile: DownloadManager.(FileEntry) -> Unit = { }

    var status: ConnectionStatus =
        ConnectionStatus.Stopped
        private set

    fun start(server: String = "192.168.2.1", port: Int = 9003) = GlobalScope.launch {
        if (status != ConnectionStatus.Stopped) return@launch
        status = ConnectionStatus.Starting
        for (i in (1..10)) {
            try {
                _client = Socket().also {
                    it.connect(InetSocketAddress(server, port))
                }
                _receiveThread = Thread(Runnable { receiveThread() }).also {
                    it.start()
                }
                return@launch
            } catch (e: Exception) {
                _client?.close()
                delay(333)
            }
        }
        stop()
    }

    fun stop() {
        requestStop(null)
    }

    suspend fun <T : Download> putRequest(request: T): T {
        continueRequest(request)
        return request.task.await() as T
    }

    private fun <T : Download> continueRequest(request: T) {
        synchronized(_downloadSession)
        {
            if (_downloadSession.size < MaxConcurrentJobs) runJobLocked(request)
            else _downloadQueue.add(request)
        }
    }

    private fun runJobLocked(job: Download) {
        _downloadSession[++_counter] = job
        _client?.getOutputStream()?.write(job.request(_counter))
    }

    private fun dequeueJobLocked() {
        if (_downloadQueue.size == 0) return
        if (_downloadSession.size < MaxConcurrentJobs) runJobLocked(_downloadQueue.poll()!!)
    }

    private fun requestStop(client: Socket?) {
        if (client != null && _client != client) return
        status = ConnectionStatus.Stopping
        _receiveThread = null
        _client?.close()
        _client = null
        onStopped()
    }

    private fun receiveThread() {
        val client = _client ?: return onError(Exception())
        val input = client.getInputStream()
        try {
            while (true) {
                val header = ByteArray(8)
                input.read(header)
                val size = header.int(4)
                val payload = ByteArray(size)
                var total = 0
                while (total < size) {
                    total += input.read(payload, total, size - total)
                }
                if (DEBUG) {
                    Log.i(
                        "ConnMgr",
                        "Len: ${payload.size}, Header: ${header.dump(4)} Data: ${payload.dump()}"
                    )
                }
                if (status == ConnectionStatus.Starting) {
                    onStarted()
                }
                if (header[2] == 'O'.toByte() && header[3] == 'W'.toByte()) {
                    handleOW(payload)
                } else if (header[2] == 'N'.toByte() && header[3] == 'W'.toByte()) {
                    handleNW(payload)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
            requestStop(client)
        }
    }

    private fun handleOW(payload: ByteArray) {
        val seq = payload.short(16)
        synchronized(_downloadSession)
        {
            val handler = _downloadSession[seq]!!
            if (handler.handle(payload)) {
                _downloadSession.remove(seq)
                if (handler.continuation) continueRequest(handler)
                else dequeueJobLocked()
            }
        }
    }

    private fun handleNW(payload: ByteArray) {
        if (payload.int(0) != 0x08050003) return
        if (payload[4] == 0.toByte()) return
        val content = payload.copyOfRange(5, 5 + payload[4])
        if (content[2] != 'E'.toByte()) return
        val isJpeg = content[3] == 0x08.toByte()
        val fileSize = content.int(4)
        val filePath = content.decodeToString(9, 9 + content[8])
        val tiff = arrayOf<Byte>(0, 0, 0, 0, 0, 0, 0, 0).toByteArray()
        onNewFile(FileEntry(filePath, "", isJpeg, fileSize, LocalDateTime.now(), TinyTiff(tiff)))
    }

    private fun onStarted() {
        status = ConnectionStatus.Started
        dispatchOnMainThread { started(this) }
    }

    private fun onStopped() {
        status = ConnectionStatus.Stopped
        dispatchOnMainThread { stopped(this) }
    }

    private fun onError(e: Exception) {
        dispatchOnMainThread { error(this, e) }
    }

    private fun onNewFile(entry: FileEntry) {
        dispatchOnMainThread { newFile(this, entry) }
    }

    private fun dispatchOnMainThread(body: () -> Unit) {
        _handler.post { body() }
    }
}
