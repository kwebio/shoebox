package com.github.sanity.shoebox

import java.util.concurrent.ConcurrentHashMap

/**
 * Created by stefan on 15.03.17.
 */

abstract class AStore<T : Any>( ) {

    private val keySpecificChangeListeners = ConcurrentHashMap<String, ConcurrentHashMap<Long, (T, T, Boolean) -> Unit>>()
    private val newListeners = ConcurrentHashMap<Long, (KeyValue<T>, Boolean) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (KeyValue<T>, Boolean) -> Unit>()
    private val changeListeners = ConcurrentHashMap<Long, (T, KeyValue<T>, Boolean) -> Unit>()

    /** retrieve all elements **/
    abstract val entries: Iterable<KeyValue<T>>

    /** retrieve element by key **/
    abstract operator fun get(key: String): T?

    fun remove(key: String) {
        val oldValue = get(key)
        remove(key,oldValue)
        if (oldValue!=null) {
            removeListeners.values.forEach { t -> t(KeyValue(key, oldValue), true) }
        }
    }

    abstract fun remove(key:String, oldValue:T?)

    operator fun set(key: String, value: T) {
        val previousValue = get(key)
        if (previousValue == null) {
            newListeners.values.forEach { l -> l(KeyValue(key, value), true) }
        } else if (value != previousValue) {
            changeListeners.values.forEach { cl -> cl(previousValue, KeyValue(key, value), true) }
            keySpecificChangeListeners[key]?.values?.forEach { l -> l(previousValue, value, true) }
        }
        if (value != previousValue) {
            store(key, value)
        }
    }

    abstract fun store(key:String, value:T)


    fun onNew(listener: (KeyValue<T>, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        newListeners.put(handle, listener)
        return handle
    }

    fun deleteNewListener(handle : Long) {
        newListeners.remove(handle)
    }

    fun onRemove(listener: (KeyValue<T>, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun deleteRemoveListener(handle : Long) {
        removeListeners.remove(handle)
    }

    fun onChange(listener: (T, KeyValue<T>, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        changeListeners.put(handle, listener)
        return handle
    }

    fun onChange(key: String, listener: (T, T, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        keySpecificChangeListeners.computeIfAbsent(key, { ConcurrentHashMap() }).put(handle, listener)
        return handle
    }

    fun deleteChangeListener(handle : Long) {
        changeListeners.remove(handle)
    }

    fun deleteChangeListener(key: String, handle : Long) {
        keySpecificChangeListeners[key]?.let {
            it.remove(handle)
            if (it.isEmpty()) {
                keySpecificChangeListeners.remove(key)
            }
        }
    }

}
