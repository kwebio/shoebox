package com.github.sanity.shoebox.generic

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

/**
 * Created by stefan on 15.03.17.
 */

abstract class ACachedStore<T : Any>( ) : AStore<T>() {

    internal val cache: LoadingCache<String, T?> = CacheBuilder.newBuilder().build<String, T?>(
            object : CacheLoader<String, T?>() {
                override fun load(key: String): T? {
                    return this@ACachedStore.loadFromPersistence(key)
                }
            }
    )

    override operator fun get(key: String): T? {
        return cache.getIfPresent(key) ?: loadFromPersistence(key) .also { if (it!=null) cache.put(key, it) }
    }

    abstract protected fun loadFromPersistence(key: String): T?

    override fun remove(key: String, oldValue:T?) {
        removeFromPersistence(key,oldValue)
        cache.invalidate(key)
    }

    abstract protected fun removeFromPersistence(key:String, oldValue:T?);


    override fun store(key: String, value: T) {
        cache.put(key, value)
        storeToPersistence(key,value)
    }
    abstract protected fun storeToPersistence(key: String, value: T);

}