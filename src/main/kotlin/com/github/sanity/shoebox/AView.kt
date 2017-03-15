package com.github.sanity.shoebox

import java.util.concurrent.ConcurrentHashMap

/**
 * Created by stefan on 15.03.17.
 */
abstract class AView<T : Any>(val viewOf: AStore<T>, val viewBy: (T) -> String) {
    abstract operator fun get(viewKey: String): Set<T>

    private val addListeners = ConcurrentHashMap<String, MutableMap<Long, (KeyValue<T>) -> Unit>>()

    fun onAdd(viewKey : String, listener : (KeyValue<T>) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        addListeners.computeIfAbsent(viewKey, { ConcurrentHashMap() }).put(handle, listener)
        return handle
    }
    fun deleteAddListener(viewKey : String, handle : Long) {
        addListeners.get(viewKey)?.remove(handle)
    }
    protected fun onAdd(viewKey: String, keyValue: KeyValue<T>) = addListeners[viewKey]?.values?.forEach { it(keyValue) }

    private val removeListeners = ConcurrentHashMap<String, MutableMap<Long, (KeyValue<T?>) -> Unit>>()

    fun onRemove(viewKey : String, listener : (KeyValue<T?>) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.computeIfAbsent(viewKey, { ConcurrentHashMap() }).put(handle, listener)
        return handle
    }

    fun deleteRemoveListener(viewKey : String, handle : Long) {
        removeListeners.get(viewKey)?.remove(handle)
    }

    protected fun onRemove(viewKey : String, keyValue: KeyValue<T?>) = removeListeners[viewKey]?.values?.forEach { it(keyValue) }

}