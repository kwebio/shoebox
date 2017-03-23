package com.github.sanity.shoebox.stores

import com.github.sanity.shoebox.KeyValue
import com.github.sanity.shoebox.Store
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ian on 3/22/17.
 */
class MemoryStore<T : Any> : Store<T>  {
    private val map = ConcurrentHashMap<String, T>()

    override val entries: Iterable<KeyValue<T>>
        get() = map.entries.map {KeyValue(it.key, it.value)}

    override fun remove(key: String): T? {
        return map.remove(key)
    }

    override fun get(key: String): T? {
        return map.get(key)
    }

    override fun set(key: String, value: T): T? {
        val previousVal = map.get(key)
        map.set(key, value)
        return previousVal
    }
}