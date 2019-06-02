package io.kweb.shoebox

import java.util.concurrent.ConcurrentHashMap

open abstract class AbstractOrderedViewSet<T : Any>(val comparator: Comparator<T>) {
    protected abstract val orderedList : MutableList<KeyValue<T>>
    protected val insertListeners = ConcurrentHashMap<Long, (Int, KeyValue<T>) -> Unit>()
    protected val removeListeners = ConcurrentHashMap<Long, (Int, KeyValue<T>) -> Unit>()
    val entries : List<T> get() = keyValueEntries.map(KeyValue<T>::value)
    val keyValueEntries : List<KeyValue<T>> = orderedList
    fun onInsert(listener : (Int, KeyValue<T>) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        insertListeners.put(handle, listener)
        return handle
    }

    fun deleteInsertListener(handle : Long) {
        insertListeners.remove(handle)
    }

    fun onRemove(listener : (Int, KeyValue<T>) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun deleteRemoveListener(handle : Long) {
        removeListeners.remove(handle)
    }
}