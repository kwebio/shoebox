package io.kweb.shoebox.stores

import com.fatboyindustrial.gsonjavatime.Converters
import com.google.common.cache.*
import com.google.gson.GsonBuilder
import io.kweb.shoebox.*
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass



/**
 * Created by ian on 3/22/17.
 */

inline fun <reified T : Any> DirectoryStore(directory : Path) = DirectoryStore(directory, T::class)

class DirectoryStore<T : Any>(val directory : Path, private val kc : KClass<T>) : Store<T> {
    companion object {
        const private val LOCK_FILENAME = "shoebox.lock"
        const private val LOCK_TOUCH_TIME_MS = 2000.toLong()
        const private val LOCK_STALE_TIME = LOCK_TOUCH_TIME_MS * 2
        private val gson = Converters.registerAll(GsonBuilder()).create()
    }

    internal val cache: LoadingCache<String, T?> = CacheBuilder.newBuilder().build<String, T?>(
            object : CacheLoader<String, T?>() {
                override fun load(key: String): T? {
                    return this@DirectoryStore.load(key)
                }
            }
    )

    private val lockFilePath = directory.resolve(LOCK_FILENAME)

    init {
        Files.createDirectories(directory)
        if (Files.exists(lockFilePath)) {
            if (System.currentTimeMillis() - Files.getLastModifiedTime(lockFilePath).toMillis() < LOCK_STALE_TIME) {
                throw RuntimeException("$directory locked by $lockFilePath")
            } else {
                Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis()))
            }
        } else {
            Files.newBufferedWriter(lockFilePath).use {
                it.appendln("locked")
            }
        }
        scheduledExecutor.scheduleWithFixedDelay({
            Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis()))
        }, LOCK_TOUCH_TIME_MS, LOCK_TOUCH_TIME_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Retrieve the entries in this store, similar to [Map.entries] but lazy
     *
     * @return The keys and their corresponding values in this [Shoebox]
     */
    override val entries: Iterable<KeyValue<T>> get() = Files.newDirectoryStream(directory)
            .mapNotNull {it.fileName.toString()}
            .filter {it != LOCK_FILENAME }
            .map {
                KeyValue(it, this[it]!!)
            }

    /**
     * Retrieve a value, similar to [Map.get]
     *
     * @param key The key associated with the desired value
     * @return The value associated with the key, or null if no value is associated
     */
    override operator fun get(key: String): T? {
        return load(key)
    }

    /**
     * Remove a key-value pair
     *
     * @param key The key associated with the value to be removed, similar to [MutableMap.remove]
     */
    override fun remove(key: String) : T? {
        val cachedValue: T? = cache.getIfPresent(key)
        if (cachedValue != null) {
            cache.invalidate(key)
        }
        val filePath = directory.resolve(key)
        if (Files.exists(filePath)) {
            val oldValue = cachedValue ?: load(key)
            if (oldValue != null) {
                Files.delete(filePath)
                return oldValue
            } else {
                return null
            }
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
    override operator fun set(key: String, value: T) : T? {
        val previousValue = get(key)
        cache.put(key, value)
        if (value != previousValue) {
            if (!directory.exists()) throw RuntimeException("Parent directory doesn't exist")
            val filePath = directory.resolve(key)
            filePath.newBufferedWriter().use {
                gson.toJson(value, kc.javaObjectType, it)
            }
        }
        return previousValue
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

    protected fun finalize() {
        Files.delete(lockFilePath)
    }
}