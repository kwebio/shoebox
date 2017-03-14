package propheto

import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by ian on 3/9/17.
 */



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