package tech.onsen.photon.views.recyclerview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class AbstractViewAdapter<T>() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    abstract fun getItem(position: Int): T
    abstract fun getLayoutId(viewType: Int): Int

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(getLayoutId(viewType), parent, false)
        return GenericViewHolder(view)
    }
}