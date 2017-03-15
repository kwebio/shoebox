package com.github.sanity.shoebox

import com.github.sanity.shoebox.generic.CachedGStore
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Created by ian on 3/9/17.

 * TODO: 1) Add a lockfile mechanism to prevent multiple JVMs or threads from
 * TODO:    using the same directory
 * TODO: 2) Handle changes that occur to the filesystem which aren't initiated here
 * TODO:    (then remove the previous lockfile mechanism)
 */

class Store<T : Any>(val parentDirectory: Path, private val kc: KClass<T>) : CachedGStore<T>() {

    init {
        Files.createDirectories(parentDirectory)
    }

    private val keySpecificChangeListeners = ConcurrentHashMap<String, ConcurrentHashMap<Long, (T, T, Boolean) -> Unit>>()
    private val newListeners = ConcurrentHashMap<Long, (KeyValue<T>, Boolean) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (KeyValue<T>, Boolean) -> Unit>()
    private val changeListeners = ConcurrentHashMap<Long, (T, KeyValue<T>, Boolean) -> Unit>()
    
    private val gson = GsonBuilder().create()

    /**
     * Return entries key-value pairs in this Store as an Iterable
     */
    override val entries: Iterable<KeyValue<T>> get() = Files.newDirectoryStream(parentDirectory)
            .mapNotNull {
                val fileKey = it.fileName.toString()
                KeyValue(fileKey, this[fileKey]!!)
            }

    fun remove(key: String) {
        val cachedValue: T? = cache.getIfPresent(key)
        val filePath = parentDirectory.resolve(key)
        if (Files.exists(filePath)) {
            val oldValue = cachedValue ?: load(key)
            if (oldValue != null) {
                Files.delete(filePath)
                removeListeners.values.forEach { t -> t(KeyValue(key, oldValue), true) }
            }
        }
        if (cachedValue != null) {
            cache.invalidate(key)
        }
    }

    override fun load(key: String): T? {
        val filePath = parentDirectory.resolve(key)
        if (Files.exists(filePath)) {
            val o = filePath.newBufferedReader().use {
               gson.fromJson(it, kc.javaObjectType)
            }
            return o
        } else {
            return null
        }
    }

    operator fun set(key: String, value: T) {
        val previousValue = get(key)
        if (previousValue == null) {
            newListeners.values.forEach { l -> l(KeyValue(key, value), true) }
        } else if (value != previousValue) {
            changeListeners.values.forEach { cl -> cl(previousValue, KeyValue(key, value), true) }
            keySpecificChangeListeners[key]?.values?.forEach { l -> l(previousValue, value, true) }
        }
        cache.put(key, value)
        if (value != previousValue) {
            if (!parentDirectory.exists()) throw RuntimeException("Parent directory doesn't exist")
            val filePath = parentDirectory.resolve(key)
            filePath.newBufferedWriter().use {
                gson.toJson(value, kc.javaObjectType, it)
            }
        }
    }

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

