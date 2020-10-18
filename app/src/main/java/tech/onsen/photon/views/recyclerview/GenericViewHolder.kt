package tech.onsen.photon.views.recyclerview

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID

class GenericViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    var currentItemId: Long = NO_ID
    var currentPosition: Int = -1

    fun getItemDetails() = ItemDetails(currentItemId, currentPosition)
}