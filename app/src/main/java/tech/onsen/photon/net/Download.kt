package tech.onsen.photon.net

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import tech.onsen.photon.helpers.asBytes
import tech.onsen.photon.helpers.dump

abstract class Download {
    companion object{
        const val Type3FR: Byte = 0x04
        const val TypeJPG: Byte = 0x01
    }

    protected var index = 0

    val task = CompletableDeferred<Download>()
    val progress = Channel<Int>()

    var continuation: Boolean = false
        protected set

    abstract fun handle(payload: ByteArray): Boolean
    abstract fun request(seq: Short): ByteArray

    protected fun makeRequest(type: Short, payload: ByteArray): ByteArray {
        val outBuffer = ByteArray(2 + 2 + 4 + payload.size)
        (0xCC55).toShort().asBytes().copyInto(outBuffer, 0)
        type.asBytes().copyInto(outBuffer, 2)
        payload.size.asBytes().copyInto(outBuffer, 4)
        payload.copyInto(outBuffer, 8)
        if(DownloadManager.DEBUG) {
            Log.i("ConnMgr", "Send: ${outBuffer.dump(64)}")
        }
        return outBuffer
    }

    protected fun send(value: Int): Job = GlobalScope.launch(Dispatchers.Main) {
        progress.send(value)
    }
}
