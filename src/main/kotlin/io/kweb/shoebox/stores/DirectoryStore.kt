package io.kweb.shoebox.stores

import com.fatboyindustrial.gsonjavatime.Converters
import com.google.common.cache.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.kweb.shoebox.*
import java.net.URLDecoder
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.time.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.reflect.KClass

/**
 * Created by ian on 3/22/17.
 */

inline fun <reified T : Any> DirectoryStore(directory : Path) = DirectoryStore(directory, T::class)

class DirectoryStore<T : Any>(val directory : Path, private val kc : KClass<T>) : Store<T> {
    companion object {
        const private val LOCK_FILENAME = "shoebox.lock"
        const private val LOCK_TOUCH_TIME_MS = 100.toLong()
        const private val LOCK_STALE_TIME = LOCK_TOUCH_TIME_MS * 2
        private val gson = Converters.registerAll(GsonBuilder()).let {
            it.registerTypeAdapter(object : TypeToken<Duration>() {}.type, DurationConverter())
        }.create()
    }

    data class CachedValueWithTime<T : Any> (val value : T, val time : Instant)

    internal val cache: LoadingCache<String, CachedValueWithTime<T>> = CacheBuilder.newBuilder().build(
            object : CacheLoader<String, CachedValueWithTime<T>>() {
                override fun load(key: String): CachedValueWithTime<T>? {
                    return this@DirectoryStore.load(key)
                }
            }
    )

    private val lockFilePath = directory.resolve(LOCK_FILENAME)

    init {
        Files.createDirectories(directory)
        if (Files.exists(lockFilePath)) {
            if (System.currentTimeMillis() - Files.getLastModifiedTime(lockFilePath).toMillis() < LOCK_STALE_TIME) {
                throw RuntimeException("$directory locked by $lockFilePath, created ${System.currentTimeMillis() - Files.getLastModifiedTime(lockFilePath).toMillis()}ms ago.")
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
            .filter {it.isNotBlank()}
            .map {
                KeyValue(URLDecoder.decode(it, "UTF-8"), this[it]!!)
            }

    /**
     * Retrieve a value, similar to [Map.get]
     *
     * @param key The key associated with the desired value
     * @return The value associated with the key, or null if no value is associated
     */
    override operator fun get(key: String): T? {
        require(key.isNotBlank()) {"key(\"$key\") must not be blank"}
        return load(key)?.value
    }

    /**
     * Remove a key-value pair
     *
     * @param key The key associated with the value to be removed, similar to [MutableMap.remove]
     */
    override fun remove(key: String) : T? {
        require(key.isNotBlank()) {"key(\"$key\") must not be blank"}
        val cachedValue: T? = cache.getIfPresent(key)?.value
        if (cachedValue != null) {
            cache.invalidate(key)
        }
        val filePath = directory.resolve(key)
        if (Files.exists(filePath)) {
            val oldValue = cachedValue ?: load(key)?.value
            return if (oldValue != null) {
                Files.delete(filePath)
                oldValue
            } else {
                null
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
        require(key.isNotBlank()) {"key(\"$key\") must not be blank"}
        val previousValue = get(key)
        cache.put(key, CachedValueWithTime(value, Instant.now()))
        if (value != previousValue) {
            if (!directory.exists()) throw RuntimeException("Parent directory doesn't exist")
            val filePath = toPath(key)
            filePath.newBufferedWriter().use {
                gson.toJson(value, kc.javaObjectType, it)
            }
        }
        return previousValue
    }

    private fun load(key: String): CachedValueWithTime<T>? {
        val filePath = toPath(key)
        cache.get(key).let { cached ->
            if (cached != null && !cached.time.isAfter(Files.getLastModifiedTime(filePath).toInstant())) return cached
        }
        return if (Files.exists(filePath)) {
            if (Files.isDirectory(filePath)) {
                throw IllegalStateException("File $filePath is a directory, not a file")
            }
            val o = filePath.newBufferedReader().use {
                gson.fromJson(it, kc.javaObjectType)
            }
            val cachedValueWithTime = CachedValueWithTime(o, Files.getLastModifiedTime(filePath).toInstant())
            cache.put(key, cachedValueWithTime)
            cachedValueWithTime
        } else {
            null
        }
    }

    fun toPath(unsanitizedKey: String): Path {
        require(unsanitizedKey.isNotBlank()) { "key(\"$unsanitizedKey\") must not be blank" }
        val key = escapeStringAsFilename(unsanitizedKey)
        val filePath = directory.resolve(key)
        return filePath
    }

    protected fun finalize() {
        Files.delete(lockFilePath)
    }
}

private val PATTERN = Pattern.compile("[^A-Za-z0-9_\\-]")

private val MAX_LENGTH = 127

internal fun escapeStringAsFilename(`in`: String): String {

    val sb = StringBuffer()

    // Apply the regex.
    val m = PATTERN.matcher(`in`)

    while (m.find()) {

        // Convert matched character to percent-encoded.
        val replacement = "%" + Integer.toHexString(m.group()[0].toInt()).toUpperCase()

        m.appendReplacement(sb, replacement)
    }
    m.appendTail(sb)

    val encoded = sb.toString()

    // Truncate the string.
    val end = Math.min(encoded.length, MAX_LENGTH)
    return encoded.substring(0, end)
}