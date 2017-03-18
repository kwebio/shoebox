package com.github.sanity.shoebox

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


 /*
 * TODO: 1) Add a lockfile mechanism to prevent multiple JVMs or threads from
 * TODO:    using the same directory
 * TODO: 2) Handle changes that occur to the filesystem which aren't initiated here
 * TODO:    (then remove the previous lockfile mechanism)
 */

/**
 * Create a [Store], use this in preference to the Store constructor to avoid having to provide a `KClass`
 *
 * @param T The type of the objects to store, these must be serializable with [Gson](https://github.com/google/gson),
 *
 * @param directory The path to a directory in which data will be stored, will be created if it doesn't already exist
 **/
inline fun <reified T : Any> Store(directory : Path) = Store(directory, T::class)

/**
 * Can persistently store and retrieve objects, and notify listeners of changes to those objects
 *
 * @constructor You probably want to use `Store<T>(directory)` instead
 * @param T The type of the objects to store, these must be serializable with [Gson](https://github.com/google/gson),
 * @param directory The path to a directory in which data will be stored, will be created if it doesn't already exist
 * @param kc The KClass associated with T.  To avoid having to provide this use `Store<T>(directory)`
 */
class Store<T : Any>(val directory: Path, private val kc: KClass<T>) {

    init {
        Files.createDirectories(directory)
    }

    internal val cache: LoadingCache<String, T?> = CacheBuilder.newBuilder().build<String, T?>(
            object : CacheLoader<String, T?>() {
                override fun load(key: String): T? {
                    return this@Store.load(key)
                }
            }
    )

    private val keySpecificChangeListeners = ConcurrentHashMap<String, ConcurrentHashMap<Long, (T, T, Source) -> Unit>>()
    private val newListeners = ConcurrentHashMap<Long, (KeyValue<T>, Source) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (KeyValue<T>, Source) -> Unit>()
    private val changeListeners = ConcurrentHashMap<Long, (T, KeyValue<T>, Source) -> Unit>()
    
    private val gson = GsonBuilder().create()

    /**
     * Retrieve the entries in this store, similar to [Map.entries] but lazy
     *
     * @return The keys and their corresponding values in this [Store]
     */
    val entries: Iterable<KeyValue<T>> get() = Files.newDirectoryStream(directory)
            .mapNotNull {
                val fileKey = it.fileName.toString()
                KeyValue(fileKey, this[fileKey]!!)
            }

    /**
     * Retrieve a value, similar to [Map.get]
     *
     * @param key The key associated with the desired value
     * @return The value associated with the key, or null if no value is associated
     */
    operator fun get(key: String): T? {
        return cache.getIfPresent(key) ?: load(key)
    }

    /**
     * Remove a key-value pair
     *
     * @param key The key associated with the value to be removed, similar to [MutableMap.remove]
     */
    fun remove(key: String) {
        val cachedValue: T? = cache.getIfPresent(key)
        val filePath = directory.resolve(key)
        if (Files.exists(filePath)) {
            val oldValue = cachedValue ?: load(key)
            if (oldValue != null) {
                Files.delete(filePath)
                removeListeners.values.forEach { t -> t(KeyValue(key, oldValue), Source.LOCAL) }
            }
        }
        if (cachedValue != null) {
            cache.invalidate(key)
        }
    }

    private fun load(key: String): T? {
        val filePath = directory.resolve(key)
        if (Files.exists(filePath)) {
            val o = filePath.newBufferedReader().use {
               gson.fromJson(it, kc.javaObjectType)
            }
            cache.put(key, o)
            return o
        } else {
            return null
        }
    }

    /**
     * Set or change a value, simliar to [MutableMap.set]
     *
     * @param key The key associated with the value to be set or changed
     * @param value The new value
     */
    operator fun set(key: String, value: T) {
        val previousValue = get(key)
        if (previousValue == null) {
            newListeners.values.forEach { l -> l(KeyValue(key, value), Source.LOCAL) }
        } else if (value != previousValue) {
            changeListeners.values.forEach { cl -> cl(previousValue, KeyValue(key, value), Source.LOCAL) }
            keySpecificChangeListeners[key]?.values?.forEach { l -> l(previousValue, value, Source.LOCAL) }
        }
        cache.put(key, value)
        if (value != previousValue) {
            if (!directory.exists()) throw RuntimeException("Parent directory doesn't exist")
            val filePath = directory.resolve(key)
            filePath.newBufferedWriter().use {
                gson.toJson(value, kc.javaObjectType, it)
            }
        }
    }

    /**
     * Add a listener for when a new key-value pair are added to the Store
     *
     * @param listener The listener to be called
     */
    fun onNew(listener: (KeyValue<T>, Source) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        newListeners.put(handle, listener)
        return handle
    }

    fun deleteNewListener(handle : Long) {
        newListeners.remove(handle)
    }

    fun onRemove(listener: (KeyValue<T>, Source) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun deleteRemoveListener(handle : Long) {
        removeListeners.remove(handle)
    }
    
    fun onChange(listener: (T, KeyValue<T>, Source) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        changeListeners.put(handle, listener)
        return handle
    }
    
    fun onChange(key: String, listener: (T, T, Source) -> Unit) : Long {
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

/**
 * The source of the event that generated this change
 */
enum class Source {
    /**
     * The event was due to a modification initiated by a call to this instance's [Store.set]
     */
    LOCAL,
    /**
     * The event was due to a filesystem change external to this instance
     */
    REMOTE
}