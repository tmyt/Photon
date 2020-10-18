package tech.onsen.photon.net

import tech.onsen.photon.helpers.int

class DownloadJpeg : DownloadFile() {
    companion object {
        private const val DefaultChunkSize = 0x300000
    }

    override fun handle(payload: ByteArray): Boolean {
        val total = payload.int(4)
        val received = payload.int(8)
        val length = payload.int(12)
        output.write(payload, 32, payload.size - 32)
        send(percent(received + length, total))
        if (received + length == total) {
            task.complete(this)
            return true
        }
        return false
    }

    override fun request(seq: Short): ByteArray {
        return makeFileRequest(DefaultChunkSize, seq, TypeJPG, path)
    }
}