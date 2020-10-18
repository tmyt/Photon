package tech.onsen.photon.views.recyclerview

abstract class ArrayAdapter<T>() : AbstractViewAdapter<T>() {
    private val _items = ArrayList<T>()
    val items: List<T> get() = _items

    // required functions
    override fun getItem(position: Int): T = _items[position]

    override fun getItemCount(): Int = _items.size

    // array manipulation
    fun add(value: T) {
        _items.add(value)
        notifyItemInserted(_items.size - 1)
    }

    fun addAll(values: Collection<T>){
        val start = _items.size
        _items.addAll(values)
        notifyItemRangeInserted(start, values.size)
    }

    fun insert(index: Int, value: T) {
        _items.add(index, value)
        notifyItemInserted(index)
    }

    fun remove(index: Int){
        _items.removeAt(index)
        notifyItemRemoved(index)
    }

    operator fun get(index: Int): T{
        return _items[index]
    }

    operator fun set(index: Int, value: T){
        _items[index] = value
        notifyItemChanged(index)
    }
}