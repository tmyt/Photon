package tech.onsen.photon.views.recyclerview

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CustomGridLayoutManager(private val context: Context) : GridLayoutManager(context, 1){
    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int
    ) {
        super.onMeasure(recycler, state, widthSpec, heightSpec)
        val width = View.MeasureSpec.getSize(widthSpec)
        val widthDp = width / context.resources.displayMetrics.density
        spanCount = (widthDp / 120).toInt()
    }
}