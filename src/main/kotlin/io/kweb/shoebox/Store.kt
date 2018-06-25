package io.kweb.shoebox

/**
 * Created by ian on 3/22/17.
 */
interface Store<T> {
    val entries: Iterable<KeyValue<T>>
    fun remove(key: String): T?
    operator fun get(key: String): T?
    operator fun set(key: String, value: T) : T?
}