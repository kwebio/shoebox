package com.github.sanity.shoebox.filebased

import com.github.sanity.shoebox.*
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Created by ian on 3/9/17.

 * TODO: 1) Add a lockfile mechanism to prevent multiple JVMs or threads from
 * TODO:    using the same directory
 * TODO: 2) Handle changes that occur to the filesystem which aren't initiated here
 * TODO:    (then remove the previous lockfile mechanism)
 */

class Store<T : Any>(val parentDirectory: Path, private val kc: KClass<T>) : ACachedStore<T>() {

    init {
        Files.createDirectories(parentDirectory)
    }
    
    private val gson = GsonBuilder().create()

    /**
     * Return entries key-value pairs in this Store as an Iterable
     */
    override val entries: Iterable<KeyValue<T>> get() = Files.newDirectoryStream(parentDirectory)
            .mapNotNull {
                val fileKey = it.fileName.toString()
                KeyValue(fileKey, this[fileKey]!!)
            }

    override fun removeFromPersistence(key: String, oldValue:T?) {
        val filePath = parentDirectory.resolve(key)
        if (Files.exists(filePath)) {
            if (oldValue != null) {
                Files.delete(filePath)
            }
        }
    }

    override fun loadFromPersistence(key: String): T? {
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

    override fun storeToPersistence(key: String, value: T) {
        if (!parentDirectory.exists()) throw RuntimeException("Parent directory doesn't exist")
        val filePath = parentDirectory.resolve(key)
        filePath.newBufferedWriter().use {
            gson.toJson(value, kc.javaObjectType, it)
        }
    }

}

