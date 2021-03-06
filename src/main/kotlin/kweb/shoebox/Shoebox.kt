package kweb.shoebox

import kotlinx.serialization.KSerializer
import kweb.shoebox.Source.LOCAL
import kweb.shoebox.View.Reference
import kweb.shoebox.View.VerifyBehavior
import kweb.shoebox.View.VerifyBehavior.BLOCKING_VERIFY
import kweb.shoebox.stores.DirectoryStore
import kweb.shoebox.stores.MapDBStore
import kweb.shoebox.stores.MemoryStore
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/*
* TODO: 1) Add a lockfile mechanism to prevent multiple JVMs or threads from
* TODO:    using the same directory
* TODO: 2) Handle changes that occur to the filesystem which aren't initiated here
* TODO:    (then remove the previous lockfile mechanism)
*/

fun <T : Any> shoebox(dir: Path, kSerializer: KSerializer<T>) = Shoebox(DirectoryStore(dir, kSerializer))

/**
 * Can persistently store and retrieve objects, and notify listeners of changes to those objects
 *
 * @constructor You probably want to use `Shoebox<T>(directory)` instead
 * @param T The type of the objects to store, these must be serializable with [Gson](https://github.com/google/gson),
 * @param directory The path to a directory in which data will be stored, will be created if it doesn't already exist
 * @param kc The KClass associated with T.  To avoid having to provide this use `Shoebox<T>(directory)`
 */
class Shoebox<T : Any>(val store: Store<T>) {

    constructor() : this(MemoryStore())

    private val keySpecificChangeListeners = ConcurrentHashMap<String, ConcurrentHashMap<Long, (T, T, Source) -> Unit>>()
    private val newListeners = ConcurrentHashMap<Long, (KeyValue<T>, Source) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (KeyValue<T>, Source) -> Unit>()
    private val changeListeners = ConcurrentHashMap<Long, (T, KeyValue<T>, Source) -> Unit>()

    /**
     * Retrieve a value, similar to [Map.get]
     *
     * @param key The key associated with the desired value
     * @return The value associated with the key, or null if no value is associated
     */
    operator fun get(key: String): T? {
        try {
            return store.get(key)
        } catch (e: Exception) {
            throw RuntimeException("Exception in call to get(\"$key\")", e)
        }
    }

    /**
     * Remove a key-value pair
     *
     * @param key The key associated with the value to be removed, similar to [MutableMap.remove]
     */
    fun remove(key: String): T? {
        val removed = store.remove(key)
        if (removed != null) {
            removeListeners.values.forEach { it.invoke(KeyValue(key, removed), LOCAL) }
        }
        return removed
    }

    /**
     * Set or change a value, simliar to [MutableMap.set]
     *
     * @param key The key associated with the value to be set or changed
     * @param value The new value
     */
    operator fun set(key: String, value: T) {
        val previousValue = store.set(key, value)
        if (previousValue == null) {
            newListeners.values.forEach { l ->
                try {
                    l(KeyValue(key, value), LOCAL)
                } catch (e: Exception) {
                    e.printStackTrace(System.err)
                }
            }
        } else if (value != previousValue) {
            changeListeners.values.forEach { cl -> cl(previousValue, KeyValue(key, value), LOCAL) }
            keySpecificChangeListeners[key]?.values?.forEach { l ->
                try {
                    l(previousValue, value, LOCAL)
                } catch (e: Exception) {
                    e.printStackTrace(System.err)
                }
            }
        }
    }

    val keysForSynchronization = WeakHashMap<String, String>()
    /**
     * A utility method to make it easier to modify an existing item, atomically
     */
    fun modify(key: String, modifier: (T) -> T): Boolean {
        val keyForSync = keysForSynchronization.computeIfAbsent(key) { key }
        synchronized(keyForSync) {
            val oldValue = this[key]
            return if (oldValue == null) {
                false
            } else {
                this[key] = modifier(oldValue)
                true
            }
        }
    }

    val entries get() = store.entries

    /**
     * Add a listener for when a new key-value pair are added to the Shoebox
     *
     * @param listener The listener to be called
     */
    fun onNew(listener: (KeyValue<T>, Source) -> Unit): Long {
        val handle = listenerHandleSource.incrementAndGet()
        newListeners.put(handle, listener)
        return handle
    }

    fun deleteNewListener(handle: Long) {
        newListeners.remove(handle)
    }

    fun onRemove(listener: (KeyValue<T>, Source) -> Unit): Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun deleteRemoveListener(handle: Long) {
        removeListeners.remove(handle)
    }

    fun onChange(listener: (T, KeyValue<T>, Source) -> Unit): Long {
        val handle = listenerHandleSource.incrementAndGet()
        changeListeners.put(handle, listener)
        return handle
    }

    fun onChange(key: String, listener: (T, T, Source) -> Unit): Long {
        val handle = listenerHandleSource.incrementAndGet()
        keySpecificChangeListeners.computeIfAbsent(key, { ConcurrentHashMap() }).put(handle, listener)
        return handle
    }

    fun deleteChangeListener(handle: Long) {
        changeListeners.remove(handle)
    }

    fun deleteChangeListener(key: String, handle: Long) {
        keySpecificChangeListeners[key]?.let {
            it.remove(handle)
            if (it.isEmpty()) {
                keySpecificChangeListeners.remove(key)
            }
        }
    }

    fun atomicModify(modification: (T) -> T) {

    }

    fun view(name: String, by: (T) -> String, verify: VerifyBehavior = BLOCKING_VERIFY): View<T> {
        val store = when (store) {
            is MemoryStore<T> -> MemoryStore<Reference>()
            is DirectoryStore<T> ->
                DirectoryStore(store.directory.parent.resolve("${store.directory.fileName}-$name-view"), Reference.serializer())
            is MapDBStore<T> -> MapDBStore(store.db, "${store.name}-$name-view", Reference.serializer())
            else -> throw RuntimeException("Shoebox doesn't currently support creating a view for store type ${store::class.simpleName}")
        }
        return View<T>(Shoebox(store), this, verify, by)
    }
}

/**
 * The source of the event that generated this change
 */
enum class Source {
    /**
     * The event was due to a modification initiated by a call to this instance's [Shoebox.set]
     */
    LOCAL,

    /**
     * The event was due to a filesystem change external to this instance
     */
    REMOTE
}