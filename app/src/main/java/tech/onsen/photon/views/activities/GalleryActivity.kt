package tech.onsen.photon.views.activities

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import com.microsoft.device.dualscreen.core.ScreenHelper
import kotlinx.android.synthetic.main.activity_main.*
import tech.onsen.photon.R
import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.views.fragments.EmptyFragment
import tech.onsen.photon.views.fragments.GalleryFragment
import tech.onsen.photon.views.fragments.PreviewFragment

class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        setSupportActionBar(toolbar)
        if (ScreenHelper.isDualMode(this)) {
            setupDualMode()
        } else {
            setupSingleMode()
        }
    }

    fun openPreview(fileEntry: FileEntry) {
        if (ScreenHelper.isDualMode(this)) {
            openPreviewInDualMode(fileEntry)
        } else {
            openPreviewInSingleMode(fileEntry)
        }
    }

    private fun setupSingleMode() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.first_container_id, GalleryFragment(), null)
            .commit()
    }

    private fun setupDualMode() {
        setupSingleMode()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.second_container_id, EmptyFragment(), null)
            .commit()
    }

    private fun openPreviewInSingleMode(fileEntry: FileEntry) {
        replaceDetailFragment(R.id.first_container_id, fileEntry)
    }

    private fun openPreviewInDualMode(fileEntry: FileEntry) {
        replaceDetailFragment(R.id.second_container_id, fileEntry)
    }

    private fun replaceDetailFragment(@IdRes containerId: Int, fileEntry: FileEntry) {
        val details = PreviewFragment.newInstance(fileEntry.localPath)
        supportFragmentManager.beginTransaction()
            .replace(containerId, details, null)
            .addToBackStack(null)
            .commit()
    }
}