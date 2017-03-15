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
    private val modificationHandlers = ArrayList<Long>()
    private val additionHandle: Long
    private val removalHandle: Long

    init {
        val ol = ArrayList<KeyValue<T>>()
        val kvComparator : Comparator<KeyValue<T>> = Comparator<KeyValue<T>> { o1, o2 -> comparator.compare(o1.value, o2.value) }
        ol.addAll(view.getKeyValues(viewKey))
        ol.sortWith(kvComparator)
        orderedList = ol
        additionHandle = view.onAdd(viewKey) { keyValue ->
            val binarySearchResult = orderedList.betterBinarySearch(keyValue, kvComparator)
            val insertionPoint: Int = when (binarySearchResult) {
                is BinarySearchResult.Exact -> throw RuntimeException("Listener called for value already in list ($keyValue)")
                is BinarySearchResult.Between -> binarySearchResult.highIndex
            }
            ol.add(insertionPoint, keyValue)
            insertListeners.values.forEach { it(insertionPoint, keyValue) }
        }

        removalHandle = view.onRemove(viewKey) { keyValue ->
            if (keyValue.value != null) {
                val binarySearchResult = orderedList.betterBinarySearch(keyValue as KeyValue<T>, kvComparator)
                when (binarySearchResult) {
                    is BinarySearchResult.Exact -> {
                        removeListeners.values.forEach { it(binarySearchResult.index, keyValue) }
                        orderedList.removeAt(binarySearchResult.index)
                    }
                    is BinarySearchResult.Between -> throw RuntimeException("remove listener called for unknown value")
                }
            } else {
                // On very rare occasions the View callback doesn't supply the value that was removed, in this case
                // there isn't much we can do, so just ignore it
            }
        }

        ol.forEach { kv ->
            modificationHandlers.add(view.viewOf.onChange(kv.key) {oldValue, newValue, _ ->
                if (comparator.compare(oldValue, newValue) != 0) {
                    val newKeyValue = KeyValue(kv.key, newValue)
                    val insertPoint = orderedList.betterBinarySearch(newKeyValue, kvComparator)
                    val insertionIndex: Int = when (insertPoint) {
                        is BinarySearchResult.Exact -> throw RuntimeException("Object modified to same value as an existing object ($newValue)")
                        is BinarySearchResult.Between -> insertPoint.highIndex
                    }
                    insertListeners.values.forEach { it(insertionIndex, newKeyValue) }

                    val oldKeyValue = KeyValue(kv.key, oldValue)
                    val removePoint = orderedList.betterBinarySearch(oldKeyValue, kvComparator)
                    val removalIndex = when (removePoint) {
                        is BinarySearchResult.Exact -> removePoint.index
                        is BinarySearchResult.Between -> throw RuntimeException("Object modified from an unknown value ($oldValue)")
                    }
                    removeListeners.values.forEach { it(removalIndex, oldKeyValue) }
                }
            })
        }
    }

    private val insertListeners = ConcurrentHashMap<Long, (Int, KeyValue<T>) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (Int, KeyValue<T>) -> Unit>()

    val entries : List<T> get() = keyValueEntries.map(KeyValue<T>::value)

    val keyValueEntries : List<KeyValue<T>> = orderedList

    fun onInsert(listener : (Int, KeyValue<T>) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        insertListeners.put(handle, listener)
        return handle
    }

    fun removeInsertListener(handle : Long) {
        insertListeners.remove(handle)
    }

    fun onRemove(listener : (Int, KeyValue<T>) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun removeRemoveListener(handle : Long) {
        removeListeners.remove(handle)
    }

    protected fun finalize() {
        view.deleteAddListener(viewKey, additionHandle)
        view.deleteRemoveListener(viewKey, removalHandle)
    }
}