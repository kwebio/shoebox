package com.github.sanity.shoebox

import propheto.listenerHandleSource
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Created by ian on 3/11/17.
 */

class View<T : Any>(val parentDirectory: Path,
                    val viewOf: Store<T>,
                    val verifyBehavior: VerifyBehavior = VerifyBehavior.BLOCKING_VERIFY,
                    val viewBy: (T) -> String) {

    internal val references = Store<Reference>(parentDirectory, Reference::class)

    private val newListenerHandler: Long
    private val changeListenerHandler: Long
    private var removeListenerHandler: Long

    init {
        newListenerHandler = viewOf.onNew { key, value, locallyInitiated ->
            val viewKey = viewBy(value)
            if (locallyInitiated) {
                addValue(viewKey, key)
            }
            addListeners[viewKey]?.values?.forEach { it(key, value) }

        }
        changeListenerHandler = viewOf.onChange { key, previousValue, nextValue, locallyInitiated ->
            if (locallyInitiated) {
                if (previousValue != nextValue) {
                    val previousViewKey = viewBy(previousValue)
                    val nextViewKey = viewBy(nextValue)
                    if (previousViewKey != nextViewKey) {

                        removeListeners[previousViewKey]?.values?.forEach { it(key, previousValue) }
                        removeValue(previousViewKey, key)

                        addListeners[nextViewKey]?.values?.forEach { it(key, nextValue) }
                        addValue(nextViewKey, key)
                    }
                }
            }
        }
        removeListenerHandler = viewOf.onRemove { key, value, locallyInitiated ->
            if (locallyInitiated) {
                val viewKey = viewBy(value)
                removeListeners[viewKey]?.values?.forEach { it(key, value) }
                removeValue(viewKey, key)
            }
        }

        when (verifyBehavior) {
            VerifyBehavior.BLOCKING_VERIFY -> verify()
            VerifyBehavior.ASYNC_VERIFY -> thread { verify() }
        }
    }

    private fun verify() {
        for ((key, value) in viewOf.entries) {
            val refKey = viewBy(value)
            addValue(refKey, key)
        }

        // NOTE: We don't check for superfluous references because these are found and corrected in get()
    }

    operator fun get(viewKey: String): Set<T> = getKeyValues(viewKey).map(KeyValue<T>::value).toSet()

    fun getKeyValues(viewKey: String): Set<KeyValue<T>> {
        val reference = references[viewKey]
        return reference?.keys?.mapNotNull { key ->
            val v = viewOf[key]
            if (v == null) {
                removeListeners[viewKey]?.values?.forEach { it(key, null) }
                removeValue(viewKey, key)
                null
            } else if (viewBy(v) != viewKey) {
                removeListeners[viewKey]?.values?.forEach { it(key, null) }
                removeValue(viewKey, key)
                null
            } else {
                KeyValue<T>(key, v)
            }
        }?.toSet() ?: Collections.emptySet()
    }

    private val addListeners = ConcurrentHashMap<String, MutableMap<Long, (String, T) -> Unit>>()
    private val removeListeners = ConcurrentHashMap<String, MutableMap<Long, (String, T?) -> Unit>>()

    fun onAdd(viewKey : String, listener : (String, T) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        addListeners.computeIfAbsent(viewKey, {ConcurrentHashMap()}).put(handle, listener)
        return handle
    }

    fun removeAddListener(viewKey : String, handle : Long) {
        addListeners.get(viewKey)?.remove(handle)
    }

    fun onRemove(viewKey : String, listener : (String, T?) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.computeIfAbsent(viewKey, {ConcurrentHashMap()}).put(handle, listener)
        return handle
    }

    fun removeRemoveListener(viewKey : String, handle : Long) {
        removeListeners.get(viewKey)?.remove(handle)
    }

    protected fun finalize() {
        viewOf.removeNewListener(newListenerHandler)
        viewOf.removeChangeListener(changeListenerHandler)
        viewOf.removeRemoveListener(removeListenerHandler)
    }

    sealed class EventType<T> {
        data class Add<T>(val key : String, val obj : T) : EventType<T>()
        data class Remove<T>(val key : String, val obj : T?) : EventType<T>()
    }

    internal fun addValue(key: String, value: String) {
        val oldRef = references[key] ?: Reference()
        references[key] = oldRef.addKey(value)
    }

    internal fun removeValue(key: String, value: String) {
        val oldRef = references[key]
        if (oldRef != null) {
            references[key] = oldRef.removeKey(value)
        }
    }

    enum class VerifyBehavior {
        BLOCKING_VERIFY, ASYNC_VERIFY
    }

    data class Reference(val keys: Set<String>) {
        constructor() : this(Collections.emptySet())

        fun removeKey(key: String) = Reference(keys.minus(key))

        fun addKey(key: String) = Reference(keys.plus(key))
    }
}




