package tech.onsen.photon.helpers

import tech.onsen.photon.net.DownloadIndex
import tech.onsen.photon.net.DownloadFile
import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.data.SD
import tech.onsen.photon.net.DownloadJpeg
import tech.onsen.photon.net.DownloadManager
import java.io.OutputStream

suspend fun DownloadManager.requestIndex(sd: SD): DownloadIndex {
    val request = DownloadIndex(sd.rawValue)
    return this.putRequest(request)
}

suspend fun DownloadManager.requestFile(entry: FileEntry, output: OutputStream): DownloadFile {
    val request = entry.asRequest().also {
        it.output = output
    }
    return this.putRequest(request)
}

suspend fun DownloadManager.requestJpeg(entry: FileEntry, output: OutputStream): DownloadFile {
    val request = DownloadJpeg().also {
        it.path = entry.path
        it.output = output
    }
    return this.putRequest(request)
}