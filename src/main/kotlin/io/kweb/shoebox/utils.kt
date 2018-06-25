package io.kweb.shoebox

import io.kweb.shoebox.BinarySearchResult.*
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by ian on 3/9/17.
 */

val scheduledExecutor = Executors.newScheduledThreadPool(1)

fun Path.newBufferedReader() = Files.newBufferedReader(this)

fun Path.newBufferedWriter(vararg openOptions : OpenOption) = Files.newBufferedWriter(this, *openOptions)

fun Path.exists() = Files.exists(this)

fun Path.mkdirIfAbsent() : Path {
    if (!this.exists()) {
        Files.createDirectory(this)
    }
    return this
}

val random = Random()

val listenerHandleSource = AtomicLong(0)


fun <T> List<T>.betterBinarySearch(v: T, comparator: Comparator<T>) = toBinarySearchResult(this.binarySearch(v, comparator))

fun <T> List<T>.toArrayList(): ArrayList<T> {
    return if (this is ArrayList) {
        this
    } else {
        val r = ArrayList<T>()
        r.addAll(this)
        r
    }
}

sealed class BinarySearchResult {
    class Exact(val index: Int) : BinarySearchResult() {
        override fun equals(other: Any?): Boolean {
            return if (other is Exact) {
                index == other.index
            } else {
                false
            }
        }

        override fun toString(): String {
            return "Exact($index)"
        }
    }
    class Between(val lowIndex: Int, val highIndex: Int) : BinarySearchResult() {
        override fun equals(other: Any?): Boolean {
            return if (other is Between) {
                lowIndex == other.lowIndex && highIndex == other.highIndex
            } else {
                false
            }
        }

        override fun toString(): String {
            return "Between($lowIndex, $highIndex)"
        }
    }
}


private fun toBinarySearchResult(result: Int): BinarySearchResult {
    return if (result >= 0) {
        Exact(result)
    } else {
        val insertionPoint = -result - 1
        Between(insertionPoint - 1, insertionPoint)
    }
}