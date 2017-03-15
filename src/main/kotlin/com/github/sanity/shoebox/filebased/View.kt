package com.github.sanity.shoebox.filebased

import com.github.sanity.shoebox.AView
import com.github.sanity.shoebox.KeyValue
import java.nio.file.Path
import java.util.*
import kotlin.concurrent.thread

/**
 * Created by ian on 3/11/17.
 */

class View<T : Any>(
    parentDirectory: Path,
    viewOf: Store<T>,
    verifyBehavior: VerifyBehavior = VerifyBehavior.BLOCKING_VERIFY,
    viewBy: (T) -> String
) : AView<T>( viewOf, viewBy )
{

    internal val references = Store<Reference>(parentDirectory, Reference::class)

    private val newListenerHandler: Long
    private val changeListenerHandler: Long
    private var removeListenerHandler: Long

    init {
        newListenerHandler = viewOf.onNew { keyValue, locallyInitiated ->
            val viewKey = viewBy(keyValue.value)
            if (locallyInitiated) {
                addValue(viewKey, keyValue.key)
            }
            onAdd(viewKey,keyValue)

        }
        changeListenerHandler = viewOf.onChange { previousValue, nextKeyValue, locallyInitiated ->
            if (locallyInitiated) {
                if (previousValue != nextKeyValue.value) {
                    val previousViewKey = viewBy(previousValue)
                    val nextViewKey = viewBy(nextKeyValue.value)
                    if (previousViewKey != nextViewKey) {

                        onRemove(previousViewKey, KeyValue(nextKeyValue.key, previousValue as T?))
                        removeValue(previousViewKey, nextKeyValue.key)

                        onAdd(nextViewKey,nextKeyValue)
                        addValue(nextViewKey, nextKeyValue.key)
                    }
                }
            }
        }
        removeListenerHandler = viewOf.onRemove { keyValue, locallyInitiated ->
            if (locallyInitiated) {
                val viewKey = viewBy(keyValue.value)
                onRemove(viewKey,keyValue as KeyValue<T?>)
                removeValue(viewKey, keyValue.key)
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

    override fun get(viewKey: String): Set<T> = getKeyValues(viewKey).map(KeyValue<T>::value).toSet()

    fun getKeyValues(viewKey: String): Set<KeyValue<T>> {
        val reference = references[viewKey]
        return reference?.keys?.mapNotNull { key ->
            val v = viewOf[key]
            if (v == null) {
                onRemove(viewKey, KeyValue(key,null as T?))
                removeValue(viewKey, key)
                null
            } else if (viewBy(v) != viewKey) {
                onRemove(viewKey, KeyValue(key,null as T?))
                removeValue(viewKey, key)
                null
            } else {
                KeyValue<T>(key, v)
            }
        }?.toSet() ?: Collections.emptySet()
    }

    protected fun finalize() {
        viewOf.deleteNewListener(newListenerHandler)
        viewOf.deleteChangeListener(changeListenerHandler)
        viewOf.deleteRemoveListener(removeListenerHandler)
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




