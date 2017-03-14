package com.github.sanity.shoebox

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.gson.GsonBuilder
import propheto.exists
import propheto.listenerHandleSource
import propheto.newBufferedReader
import propheto.newBufferedWriter
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



class PersistedMap<T : Any>(val parentDirectory: Path, private val kc: KClass<T>) {

    init {
        Files.createDirectories(parentDirectory)
    }

    internal val cache: LoadingCache<String, T?> = CacheBuilder.newBuilder().build<String, T?>(
            object : CacheLoader<String, T?>() {
                override fun load(name: String): T? {
                    return this@PersistedMap.load(name)
                }
            }
    )

    private val nameSpecificChangeListeners = ConcurrentHashMap<String, ConcurrentHashMap<Long, (T, T, Boolean) -> Unit>>()
    private val newListeners = ConcurrentHashMap<Long, (String, T, Boolean) -> Unit>()
    private val removeListeners = ConcurrentHashMap<Long, (String, T, Boolean) -> Unit>()
    private val changeListeners = ConcurrentHashMap<Long, (String, T, T, Boolean) -> Unit>()
    
    private val gson = GsonBuilder().create()

    val all: Iterable<KeyValue<T>> get() = Files.newDirectoryStream(parentDirectory)
            .mapNotNull {
                val fileName = it.fileName.toString()
                KeyValue(fileName, this[fileName]!!, lastModifiedTimeMS(fileName)!!)
            }

    operator fun get(name: String): T? {
        return cache.getIfPresent(name) ?: load(name)
    }

    fun remove(name: String) {
        val cachedValue: T? = cache.getIfPresent(name)
        val filePath = parentDirectory.resolve(name)
        if (Files.exists(filePath)) {
            val oldValue = cachedValue ?: load(name)
            if (oldValue != null) {
                Files.delete(filePath)
                removeListeners.values.forEach { t -> t(name, oldValue, true) }
            }
        }
        if (cachedValue != null) {
            cache.invalidate(name)
        }
    }

    private fun load(name: String): T? {
        val filePath = parentDirectory.resolve(name)
        if (Files.exists(filePath)) {
            val o = filePath.newBufferedReader().use {
               gson.fromJson(it, kc.javaObjectType)
            }
            cache.put(name, o)
            return o
        } else {
            return null
        }
    }

    operator fun set(name: String, value: T) {
        val previousValue = get(name)
        if (previousValue == null) {
            newListeners.values.forEach { l -> l(name, value, true) }
        } else if (value != previousValue) {
            changeListeners.values.forEach { cl -> cl(name, previousValue, value, true) }
            nameSpecificChangeListeners[name]?.values?.forEach { l -> l(previousValue, value, true) }
        }
        cache.put(name, value)
        if (value != previousValue) {
            if (!parentDirectory.exists()) throw RuntimeException("Parent directory doesn't exist")
            val filePath = parentDirectory.resolve(name)
            filePath.newBufferedWriter().use {
                gson.toJson(value, kc.javaObjectType, it)
            }
        }
    }

    fun lastModifiedTimeMS(name: String): Long? {
        val filePath = parentDirectory.resolve(name)
        if (Files.exists(filePath)) {
            return Files.getLastModifiedTime(filePath).toMillis()
        } else {
            return null
        }
    }

    fun onNew(listener: (String, T, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        newListeners.put(handle, listener)
        return handle
    }

    fun removeNewListener(handle : Long) {
        newListeners.remove(handle)
    }

    fun onRemove(listener: (String, T, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        removeListeners.put(handle, listener)
        return handle
    }

    fun removeRemoveListener(handle : Long) {
        removeListeners.remove(handle)
    }
    
    fun onChange(listener: (String, T, T, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        changeListeners.put(handle, listener)
        return handle
    }
    
    fun onChange(name: String, listener: (T, T, Boolean) -> Unit) : Long {
        val handle = listenerHandleSource.incrementAndGet()
        nameSpecificChangeListeners.computeIfAbsent(name, { ConcurrentHashMap() }).put(handle, listener)
        return handle
    }

    fun removeChangeListener(handle : Long) {
        changeListeners.remove(handle)
    }

    fun removeChangeListener(name: String, handle : Long) {
        nameSpecificChangeListeners[name]?.let {
            it.remove(handle)
            if (it.isEmpty()) {
                nameSpecificChangeListeners.remove(name)
            }
        }
    }
}

data class KeyValue<out V>(val key: String, val value: V, val lastModifiedMs: Long)