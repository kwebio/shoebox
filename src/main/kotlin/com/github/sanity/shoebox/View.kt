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
                    val viewOf: PersistedMap<T>,
                    val verifyBehavior: VerifyBehavior = VerifyBehavior.BLOCKING_VERIFY,
                    val viewBy: (T) -> String) {

    internal val references = PersistedMap<Reference>(parentDirectory, Reference::class)

    private val newListenerHandler: Long
    private val changeListenerHandler: Long
    private var removeListenerHandler: Long

    init {
        newListenerHandler = viewOf.onNew { name, value, locallyInitiated ->
            val viewKey = viewBy(value)
            if (locallyInitiated) {
                addValue(viewKey, name)
            }
            addListeners[viewKey]?.values?.forEach { it(name, value) }

        }
        changeListenerHandler = viewOf.onChange { name, previousValue, nextValue, locallyInitiated ->
            if (locallyInitiated) {
                if (previousValue != nextValue) {
                    val previousViewKey = viewBy(previousValue)
                    val nextViewKey = viewBy(nextValue)
                    if (previousViewKey != nextViewKey) {

                        removeListeners[previousViewKey]?.values?.forEach { it(name, previousValue) }
                        removeValue(previousViewKey, name)

                        addListeners[nextViewKey]?.values?.forEach { it(name, nextValue) }
                        addValue(nextViewKey, name)
                    }
                }
            }
        }
        removeListenerHandler = viewOf.onRemove { name, value, locallyInitiated ->
            if (locallyInitiated) {
                val viewKey = viewBy(value)
                removeListeners[viewKey]?.values?.forEach { it(name, value) }
                removeValue(viewKey, name)
            }
        }

        when (verifyBehavior) {
            VerifyBehavior.BLOCKING_VERIFY -> verify()
            VerifyBehavior.ASYNC_VERIFY -> thread { verify() }
        }
    }

    private fun verify() {
        for ((name, value, _) in viewOf.all) {
            val refName = viewBy(value)
            addValue(refName, name)
        }

        // NOTE: We don't check for superfluous references because these are found and corrected in get()
    }

    operator fun get(viewName: String): Set<T> {
        val reference = references[viewName]
        return reference?.names?.mapNotNull { name ->
            val v = viewOf[name]
            if (v == null) {
                removeListeners[viewName]?.values?.forEach { it(name, null) }
                removeValue(viewName, name)
                null
            } else if (viewBy(v) != viewName) {
                removeListeners[viewName]?.values?.forEach { it(name, null) }
                removeValue(viewName, name)
                null
            } else {
                v
            }
        }?.toSet() ?: Collections.emptySet<T>()
    }

    private val addListeners = ConcurrentHashMap<String, MutableMap<Long, (String, T) -> Unit>>()
    private val removeListeners = ConcurrentHashMap<String, MutableMap<Long, (String, T?) -> Unit>>()

    fun onAdd(viewName : String, listener : (String, T) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        addListeners.computeIfAbsent(viewName, {ConcurrentHashMap()}).put(handle, listener)
        return handle
    }

    fun removeAddListener(viewName : String, handle : Long) {
        addListeners.get(viewName)?.remove(handle)
    }

    fun onRemove(viewName : String, listener : (String, T?) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.computeIfAbsent(viewName, {ConcurrentHashMap()}).put(handle, listener)
        return handle
    }

    fun removeRemoveListener(viewName : String, handle : Long) {
        removeListeners.get(viewName)?.remove(handle)
    }

    protected fun finalize() {
        viewOf.removeNewListener(newListenerHandler)
        viewOf.removeChangeListener(changeListenerHandler)
        viewOf.removeRemoveListener(removeListenerHandler)
    }

    sealed class EventType<T> {
        data class Add<T>(val name : String, val obj : T) : EventType<T>()
        data class Remove<T>(val name : String, val obj : T?) : EventType<T>()
    }

    internal fun addValue(name: String, value: String) {
        val oldRef = references[name] ?: Reference()
        references[name] = oldRef.addName(value)
    }

    internal fun removeValue(name: String, value: String) {
        val oldRef = references[name]
        if (oldRef != null) {
            references[name] = oldRef.removeName(value)
        }
    }
}

data class Reference(val names: Set<String>) {
    constructor() : this(Collections.emptySet())

    fun removeName(name: String) = Reference(names.minus(name))

    fun addName(name: String) = Reference(names.plus(name))
}

enum class VerifyBehavior {
    BLOCKING_VERIFY, ASYNC_VERIFY
}

