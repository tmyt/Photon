package tech.onsen.photon.net

import tech.onsen.photon.helpers.asCollection
import tech.onsen.photon.helpers.encodeToByteArray
import java.io.ByteArrayOutputStream
import java.io.OutputStream

abstract class DownloadFile : Download() {
    var path: String = ""
    var output: OutputStream = ByteArrayOutputStream()

    protected fun makeFileRequest(chunkSize: Int, seq: Short, type: Byte, path: String): ByteArray {
        val payload = ArrayList<Byte>()
        payload.addAll(listOf(0x04, 0x00, 0x08, 0x05, 0x34))
        payload.addAll(seq.asCollection())
        payload.addAll(listOf(0x07, 0x0d, 0, 0, 0))
        payload.add(path.length.toByte())
        payload.addAll(path.encodeToByteArray().asList())
        payload.addAll(listOf(0, 0))
        payload.add((if ((index and 1) == 1) 0x80 else 0).toByte())
        payload.add((index shr 1).toByte())
        payload.addAll(listOf(0, 0, 0, 0))
        payload.addAll(chunkSize.asCollection())
        payload.add(type)
        return makeRequest(0x574e, payload.toByteArray())
    }

    protected fun percent(current: Int, total: Int): Int{
        return (current / (total * 100.0)).toInt()
    }
}