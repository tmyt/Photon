package tech.onsen.photon.net

import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.helpers.asCollection
import tech.onsen.photon.helpers.decodeToString
import tech.onsen.photon.helpers.int
import tech.onsen.photon.helpers.short
import tech.onsen.photon.imaging.TinyTiff

// sdIndex: 0 = SD1, 1 = SD2
class DownloadIndex(private val sdIndex: Int) : Download() {
    private var _rest: ByteArray? = null

    val table = ArrayList<FileEntry>()

    override fun handle(payload: ByteArray): Boolean {
        val total = payload.int(4)
        val received = payload.int(8)
        val length = payload.int(12)
        var index = 36 //if (received == 0) 4 else 0
        // rebuild payload
        val realPayload = _rest?.let {
            _rest = null
            val updated = ByteArray(it.size + payload.size + 4)
            it.copyInto(updated, 36)
            payload.copyInto(updated, 36 + it.size, 32)
            return@let updated
        } ?: payload
        // parse payload
        while (index < realPayload.size) {
            val offset = index //+ 32
            if (offset + 0x1A > realPayload.size) break
            val type = realPayload[offset + 0x04]
            val fileSize = realPayload.int(offset + 0x05)
            val pathLength = realPayload.short(offset + 0x13)
            val payloadSize = realPayload.short(offset + 0x17)
            if (offset + 0x1A + pathLength + payloadSize > realPayload.size) break
            val path = realPayload.decodeToString(offset + 0x1A, offset + 0x1A + pathLength)
            if (type != 0.toByte() && payloadSize > 0) {
                val head = offset + 0x1A + pathLength
                val tiff = realPayload.copyOfRange(head, head + payloadSize)
                val meta = TinyTiff(tiff)
                table.add(FileEntry(path, "", type != 0x01.toByte(), fileSize, meta.getDateTime(), meta))
            }
            index += 0x1A + pathLength + payloadSize
        }
        // check rest data
        if (index < realPayload.size) {
            _rest = ByteArray(realPayload.size - index).also {
                realPayload.copyInto(it, 0, index)
            }
        }
        if (received + length == total) {
            task.complete(this)
            return true
        }
        return false
    }

    override fun request(seq: Short): ByteArray {
        val payload = ArrayList<Byte>()
        payload.addAll(listOf(0x04, 0x00, 0x08, 0x05, 0x13))
        payload.addAll(seq.asCollection())
        payload.addAll(listOf(0x07, 0x0e, 0, 0, 0))
        payload.add(sdIndex.toByte())
        payload.addAll(listOf(0, 0, 0, 0, 0, 0, 0, 0x0a))
        payload.add(sdIndex.toByte())
        payload.addAll(listOf(0, 0))
        return makeRequest(0x574e, payload.toByteArray())
    }
}