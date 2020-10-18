package tech.onsen.photon.data

import tech.onsen.photon.imaging.TinyTiff
import tech.onsen.photon.net.DownloadJpeg
import tech.onsen.photon.net.DownloadRaw
import tech.onsen.photon.net.DownloadFile
import java.nio.file.FileSystems
import java.time.LocalDateTime

data class FileEntry(
    val path: String,
    val localPath: String,
    val isJpeg: Boolean,
    val size: Int,
    val createdAt: LocalDateTime,
    val meta: TinyTiff
) {
    val stableId by lazy {
        val name = FileSystems.getDefault().getPath(path).fileName.toString()
        val regex = Regex("\\d+")
        regex.find(name)?.value?.toLong() ?: 0
    }

    fun asRequest(): DownloadFile {
        return if (isJpeg) {
            DownloadJpeg().also {
                it.path = path
            }
        } else {
            DownloadRaw().also {
                it.path = path
                it.fileSize = size
            }
        }
    }
}