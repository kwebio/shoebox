package com.github.sanity.shoebox

import propheto.BinarySearchResult
import propheto.betterBinarySearch
import propheto.listenerHandleSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ian on 3/14/17.
 */
class OrderedViewSet<T : Any>(val view : View<T>, val viewKey : String, val comparator: Comparator<T>) {
    private val orderedList : MutableList<KeyValue<T>>
    private val additionHandle: Long
    private val removalHandle: Long

    init {
        val ol = ArrayList<KeyValue<T>>()
        val kvComparator : Comparator<KeyValue<T>> = Comparator<KeyValue<T>> { o1, o2 -> comparator.compare(o1.value, o2.value) }
        ol.addAll(view.getKeyValues(viewKey))
        ol.sortWith(kvComparator)
        orderedList = ol
        additionHandle = view.onAdd(viewKey) { key, value ->
            val binarySearchResult = orderedList.betterBinarySearch(KeyValue(key, value), kvComparator)
            val insertionPoint: Int = when (binarySearchResult) {
                is BinarySearchResult.Exact -> throw RuntimeException("Listener called for value already in list ($value)")
                is BinarySearchResult.Between -> binarySearchResult.lowIndex
            }
            ol.add(insertionPoint, KeyValue(key, value))
            insertListeners.values.forEach { it(insertionPoint, value) }
        }

        removalHandle = view.onRemove(viewKey) { key, value ->
            if (value != null) {
                val binarySearchResult = orderedList.betterBinarySearch(KeyValue(key, value), kvComparator)
                when (binarySearchResult) {
                    is BinarySearchResult.Exact -> {
                        removeListeners.values.forEach { it(binarySearchResult.index, value) }
                        orderedList.removeAt(binarySearchResult.index)
                    }
                    is BinarySearchResult.Between -> throw RuntimeException("remove listener called for unknown value")
                }
            } else {
                // On very rare occasions the View callback doesn't supply the value that was removed, in this case
                // there isn't much we can do, so just ignore it
            }
        }
    }

    private val insertListeners = ConcurrentHashMap<Long, (Int, T) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (Int, T) -> Unit>()

    fun onInsert(listener : (Int, T) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        insertListeners.put(handle, listener)
        return handle
    }

    fun removeInsertListener(handle : Long) {
        insertListeners.remove(handle)
    }

    fun onRemove(listener : (Int, T) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun removeRemoveListener(handle : Long) {
        removeListeners.remove(handle)
    }

    protected fun finalize() {
        view.removeAddListener(viewKey, additionHandle)
        view.removeRemoveListener(viewKey, removalHandle)
    }
}