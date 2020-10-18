package tech.onsen.photon.views.recyclerview

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DiffUtil

abstract class LiveDataAdapter<T>(owner: LifecycleOwner, private val source: LiveData<List<T>>) :
    AbstractViewAdapter<T>() {
    private var items: List<T> = listOf()

    init {
        items = source.value ?: listOf()
        source.observe(owner) { onDataSetChanging(it) }
    }

    private fun onDataSetChanging(newList: List<T>) {
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                areItemsTheSame(items[oldItemPosition], newList[newItemPosition])

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                areContentsTheSame(items[oldItemPosition], newList[newItemPosition])
        })
        items = newList
        result.dispatchUpdatesTo(this)
    }

    override fun getItem(position: Int): T = source.value!!.get(position)
    override fun getItemCount(): Int = source.value!!.size
    protected abstract fun areItemsTheSame(oldItem: T, newItem: T): Boolean
    protected abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean
}