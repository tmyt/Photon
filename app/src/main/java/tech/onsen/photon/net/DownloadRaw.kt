package tech.onsen.photon.net

import tech.onsen.photon.helpers.int

class DownloadRaw : DownloadFile() {
    companion object {
        private const val DefaultChunkSize = 0x800000
    }

    private var _receivedSize: Int = 0
    var fileSize: Int = 0

    override fun handle(payload: ByteArray): Boolean {
        val total = payload.int(4)
        val received = payload.int(8)
        val length = payload.int(12)

        output.write(payload, 32, payload.size - 32)
        send(percent(_receivedSize + received + length, fileSize))
        if (received + length == total) {
            _receivedSize += DefaultChunkSize
            index += 1
            continuation = _receivedSize < fileSize
            if (!continuation) task.complete(this)
            return true
        }
        return false
    }

    override fun request(seq: Short): ByteArray {
        val chunkSize = (fileSize - DefaultChunkSize * index).coerceAtMost(DefaultChunkSize)
        return makeFileRequest(chunkSize, seq, Type3FR, path)
    }
}