package tech.onsen.photon.services

import android.content.ContentValues
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.onsen.photon.app.App
import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.helpers.requestFile
import tech.onsen.photon.lifecycle.AppLifecycleOwner
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class DownloadService : LifecycleService() {
    inner class LocalBinder : Binder() {
        fun getService() = this@DownloadService
    }

    private val binder = LocalBinder()
    private var totalCount = 0
    private var downloadCount = 0
        set(value) {
            field = value
            updateNotification(value)
        }

    override fun onCreate() {
        super.onCreate()
        lifecycle.addObserver(AppLifecycleOwner)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    fun downloadFiles(files: List<FileEntry>) {
        totalCount += files.size
        downloadCount += files.size
        files.forEach { downloadFile(it) }
    }

    private fun downloadFile(fileEntry: FileEntry) = GlobalScope.launch {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            downloadFileIntoStorage(fileEntry)
        } else {
            downloadFileIntoStorage_Q(fileEntry)
        }
        if (--downloadCount == 0) {
            stopSelf()
        }
    }

    private suspend fun downloadFileIntoStorage(fileEntry: FileEntry) {
        val file = File(fileEntry.path)
        val mime = if (fileEntry.isJpeg) "image/jpeg" else "image/x-hasselblad-3fr"
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val path = "${root}/${file.name}"
        BufferedOutputStream(FileOutputStream(path)).use {
            App.current.downloadManager.requestFile(fileEntry, it)
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATA, path)
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun downloadFileIntoStorage_Q(fileEntry: FileEntry) {
        val file = File(fileEntry.path)
        val mime = if (fileEntry.isJpeg) "image/jpeg" else "image/x-hasselblad-3fr"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media
            .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val item = contentResolver.insert(collection, values)!!

        contentResolver.openFileDescriptor(item, "w", null).use {
            BufferedOutputStream(FileOutputStream(it!!.fileDescriptor)).use {
                App.current.downloadManager.requestFile(fileEntry, it)
            }
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        contentResolver.update(item, values, null, null)
    }

    private fun updateNotification(count: Int) {
        createNotificationChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Picture Download")
            .setContentText("Downloading ${totalCount - count} / $totalCount")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(totalCount, totalCount - count, false)
        NotificationManagerCompat.from(this).apply {
            if (count == 0) {
                cancel(NOTIFICATION_ID)
            } else {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Download Progress")
                .setDescription("Download progress status notification")
        NotificationManagerCompat.from(this)
            .createNotificationChannel(channel.build())
        // Register the channel with the system
        NotificationManagerCompat.from(this)
            .createNotificationChannel(channel.build())
    }

    companion object {
        private val CHANNEL_ID = "notification-channel"
        private val NOTIFICATION_ID = 1
    }
}