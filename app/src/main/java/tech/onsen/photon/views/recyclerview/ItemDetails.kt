package tech.onsen.photon.views.recyclerview

import androidx.recyclerview.selection.ItemDetailsLookup

class ItemDetails(private val id: Long, private val position: Int) : ItemDetailsLookup.ItemDetails<Long>() {
    override fun getSelectionKey(): Long? = id
    override fun getPosition(): Int = position
}