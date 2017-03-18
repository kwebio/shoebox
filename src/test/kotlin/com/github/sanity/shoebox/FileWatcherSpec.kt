package com.github.sanity.shoebox

import io.kotlintest.specs.FreeSpec
import java.nio.file.Files
import java.nio.file.StandardWatchEventKinds

/**
 * Created by ian on 3/17/17.
 */
class FileWatcherSpec : FreeSpec() {
    init {
        "File watcher should detect a file creation" {
            val dir = Files.createTempDirectory("ss-")
            val one = dir.resolve("one")
            one.mkdirIfAbsent()
            println("Directory: $one")
            var callCount = 0
            FileWatcher(one, { kind, path ->
                callCount++
                kind shouldEqual StandardWatchEventKinds.ENTRY_CREATE
                path.last().shouldEqual("dog")
            })

            Thread.sleep(1000)
            println("Writing file")
            Files.newOutputStream(one.resolve("dog")).use {
                it.write(1)
            }
            println("File written")
            Thread.sleep(1000)
            callCount shouldEqual 1
        }
    }
}
