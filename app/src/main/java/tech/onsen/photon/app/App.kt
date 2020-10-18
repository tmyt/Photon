package tech.onsen.photon.app

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.data.SD
import tech.onsen.photon.helpers.liveData
import tech.onsen.photon.helpers.requestIndex
import tech.onsen.photon.helpers.requestJpeg
import tech.onsen.photon.lifecycle.AppLifecycleOwner
import tech.onsen.photon.net.DownloadManager
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems

class App : Application(), LifecycleObserver {
    companion object {
        lateinit var current: App private set
    }

    //private var _started = false
    private var _startCount = 0

    val thumbnailData by liveData<List<FileEntry>>(listOf())
    var currentSd = SD.SD1
        private set
    lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleOwner)
        AppLifecycleOwner.lifecycle.addObserver(this)
    }

    fun startManager() {
        if (_startCount++ != 0) return
        downloadManager.start()
    }

    fun stopManager() {
        if (--_startCount != 0) return
        downloadManager.stop()
    }

    fun requestIndex(sd: SD) = lifecycleScope {
        currentSd = sd
        val index = downloadManager
            .requestIndex(sd).table
            .asReversed()
        thumbnailData.postValue(index)
        // get thumbnail jpeg images
        requestThumbnailData(index)
    }

    private suspend fun requestThumbnail(fileEntry: FileEntry): String {
        val name = FileSystems.getDefault().getPath(fileEntry.path).fileName
        val localPath = cacheDir.path + "/thumb_" + name
        // fast path
        if (File(localPath).exists()) return localPath
        // slow path
        BufferedOutputStream(FileOutputStream(localPath)).use {
            downloadManager.requestJpeg(fileEntry, it)
        }
        return localPath
    }

    private suspend fun requestThumbnailData(thumbnailData: List<FileEntry>) {
        thumbnailData.forEach {
            // fast path
            if (it.localPath.isNotEmpty()) return@forEach
            // slow path
            kotlin.run {
                val localPath = requestThumbnail(it)
                synchronizedUpdateThumbnail(it.copy(localPath = localPath))
            }
        }
    }

    private suspend fun synchronizedUpdateThumbnail(fileEntry: FileEntry) = withContext(Main) {
        val newList = thumbnailData.value!!.toMutableList()
        val i = newList.indexOfFirst { fileEntry.stableId == it.stableId }
        newList[i] = fileEntry
        thumbnailData.value = newList
    }

    private fun lifecycleScope(block: suspend CoroutineScope.() -> Unit) {
        AppLifecycleOwner.lifecycleScope.launch(
            Dispatchers.IO,
            CoroutineStart.DEFAULT,
            block
        )
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreate() {
        current = this
        downloadManager = DownloadManager()
        downloadManager.started = {
            this@App.requestIndex(currentSd)
        }
        downloadManager.newFile = {
            if ((it.path.startsWith("/SD1/") && currentSd == SD.SD1)
                || (it.path.startsWith("/SD2/") && currentSd == SD.SD2)
            ) {
                this@App.requestIndex(currentSd)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onApplicationStart() {
        if (_startCount > 0) {
            downloadManager.start()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onApplicationStop() {
        if (_startCount > 0) {
            downloadManager.stop()
        }
    }
}