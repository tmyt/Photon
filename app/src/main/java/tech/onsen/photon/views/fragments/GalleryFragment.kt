package tech.onsen.photon.views.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_finder.view.*
import kotlinx.android.synthetic.main.item_gallery.view.*
import tech.onsen.photon.R
import tech.onsen.photon.app.App
import tech.onsen.photon.data.FileEntry
import tech.onsen.photon.data.SD
import tech.onsen.photon.helpers.requireSupportActionBar
import tech.onsen.photon.services.DownloadService
import tech.onsen.photon.views.activities.GalleryActivity
import tech.onsen.photon.views.recyclerview.CustomGridLayoutManager
import tech.onsen.photon.views.recyclerview.GenericViewHolder
import tech.onsen.photon.views.recyclerview.LiveDataAdapter
import java.io.File

class GalleryFragment : Fragment() {
    private class ThumbnailListAdapter(owner: LifecycleOwner, source: LiveData<List<FileEntry>>) :
        LiveDataAdapter<FileEntry>(owner, source) {
        var onSelect: (FileEntry) -> Unit = {}
        var tracker: SelectionTracker<Long>? = null

        init {
            setHasStableIds(true)
        }

        override fun getLayoutId(viewType: Int) = R.layout.item_gallery

        override fun getItemId(position: Int): Long = getItem(position).stableId

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val itemView = holder.itemView
            val data = getItem(position)
            val localPath = data.localPath
            (holder as? GenericViewHolder)?.also {
                it.currentItemId = data.stableId
                it.currentPosition = position
            }
            if (tracker?.hasSelection() == true) {
                itemView.setOnClickListener(null)
            } else {
                itemView.setOnClickListener { onSelect(data) }
            }
            itemView.isActivated = tracker?.isSelected(data.stableId) ?: false
            itemView.image.transitionName = "image_${data.stableId}"
            if (localPath.isEmpty()) return
            Picasso.get()
                .load(File(localPath))
                //.load(localPath)
                .resize(256, 256)
                .centerCrop()
                .into(itemView.image)
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            holder.itemView.image.setImageURI(null)
            holder.itemView.image.transitionName = ""
            holder.itemView.setOnClickListener(null)
            holder.itemView.isActivated = false
        }

        override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean {
            return oldItem.stableId == newItem.stableId
        }

        override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean {
            return oldItem.localPath == newItem.localPath
        }
    }

    class ThumbnailIdDetailsLookup(
        private val recyclerView: RecyclerView
    ) : ItemDetailsLookup<Long>() {

        override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? =
            recyclerView.findChildViewUnder(
                e.x,
                e.y
            )?.let {
                (recyclerView.getChildViewHolder(it) as GenericViewHolder).getItemDetails()
            }
    }

    class ThumbnailItemKeyProvider(private val recyclerView: RecyclerView) :
        ItemKeyProvider<Long>(SCOPE_MAPPED) {
        override fun getKey(position: Int): Long? {
            return recyclerView.adapter?.getItemId(position) ?: throw Exception()
        }

        override fun getPosition(key: Long): Int {
            val viewHolder = recyclerView.findViewHolderForItemId(key);
            return viewHolder?.layoutPosition ?: RecyclerView.NO_POSITION
        }
    }

    private lateinit var _viewAdapter: ThumbnailListAdapter
    private var _tracker: SelectionTracker<Long>? = null
    private var _recyclerView: RecyclerView? = null

    private val _selectionObserver = object : SelectionTracker.SelectionObserver<Long>() {
        override fun onSelectionChanged() {
            val hasSelection = _tracker!!.hasSelection()
            val bar = requireSupportActionBar()
            bar.title = when {
                hasSelection -> "${_tracker!!.selection.size()}"
                else -> "Photon"
            }
            bar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)
            bar.setDisplayHomeAsUpEnabled(hasSelection)
            requireActivity().invalidateOptionsMenu()
            _backPressed.isEnabled = hasSelection
        }
    }

    private var _backPressed = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val tracker = _tracker
            if (tracker?.hasSelection() == true) {
                tracker.clearSelection()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_gallery, container, false)
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.current.startManager()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        initializeRecyclerView()
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, _backPressed)
//        App.current.thumbnailData.postValue(
//            listOf(
//                FileEntry(
//                    "",
//                    "https://miro.medium.com/max/875/1*MI686k5sDQrISBM6L8pf5A.jpeg",
//                    true, 1, LocalDateTime.now(), TinyTiff.emptyTiff
//                )
//            )
//        )
    }

    private fun initializeRecyclerView() {
        val viewManager = CustomGridLayoutManager(requireContext())
        _viewAdapter = ThumbnailListAdapter(viewLifecycleOwner, App.current.thumbnailData)
        _recyclerView = requireView().items.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = _viewAdapter
        }
        _tracker = SelectionTracker.Builder<Long>(
            "thumbnail-selection-id",
            _recyclerView!!,
            ThumbnailItemKeyProvider(_recyclerView!!),
            ThumbnailIdDetailsLookup(_recyclerView!!),
            StorageStrategy.createLongStorage()
        ).build()
        _tracker!!.addObserver(_selectionObserver)
        _viewAdapter.tracker = _tracker
        _viewAdapter.onSelect = { onThumbnailSelected(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        App.current.stopManager()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (_tracker?.hasSelection() == true) {
            inflater.inflate(R.menu.menu_gallery_selection, menu)
        } else {
            inflater.inflate(R.menu.menu_gallery, menu)
            when (App.current.currentSd) {
                SD.SD1 -> menu.findItem(R.id.sd_1).isChecked = true
                SD.SD2 -> menu.findItem(R.id.sd_2).isChecked = true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                (_recyclerView?.adapter as? ThumbnailListAdapter)?.tracker?.clearSelection()
                return true
            }
            R.id.download -> {
                val selection = _tracker?.selection ?: return true
                val items = App.current.thumbnailData.value ?: return true
                requestDownloadFiles(selection.map { items.firstOrNull { item -> item.stableId == it } }
                    .filterNotNull())
                _tracker?.clearSelection()
            }
            R.id.sd_1 -> {
                App.current.requestIndex(SD.SD1)
                item.isChecked = true
            }
            R.id.sd_2 -> {
                App.current.requestIndex(SD.SD2)
                item.isChecked = true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun requestDownloadFiles(files: List<FileEntry>) {
        val intent = Intent(requireContext(), DownloadService::class.java)
        requireContext().startService(intent)
        requireContext().bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
                val binder = p1 as DownloadService.LocalBinder
                val service = binder.getService()
                service.downloadFiles(files)
                requireContext().unbindService(this)
            }

            override fun onServiceDisconnected(p0: ComponentName?) = Unit
        }, Context.BIND_AUTO_CREATE)
    }

    private fun onThumbnailSelected(data: FileEntry) {
        (requireActivity() as GalleryActivity).openPreview(data)
    }
}