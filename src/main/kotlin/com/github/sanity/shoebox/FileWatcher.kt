package com.github.sanity.shoebox

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Created by ian on 3/12/17.
 */

class FileWatcher(path : Path, listener : (WatchEvent.Kind<Path>, Path) -> Unit) {
    private val listeners = ConcurrentHashMap<Path, MutableList<(WatchEvent.Kind<Path>, Path) -> Unit>>()


    init {
        TODO("Doesn't work, possibly because Java NIO doesn't work on OSX - possible solution: https://github.com/gjoseph/BarbaryWatchService")
        val service = path.fileSystem.newWatchService()
        println("Init service")
        thread {
            service.use { watchService ->
                while (true) {
                    println("Waiting for key")
                    val key = watchService.take()
                    println("Found key")
                    println("Polled")
                    if (key != null) {
                        for (rawWatchEvent in key.pollEvents()) {
                            println("Watch event")
                            val watchEvent = rawWatchEvent as WatchEvent<Path>
                            val kind = rawWatchEvent.kind()
                            if (kind == StandardWatchEventKinds.OVERFLOW) continue
                            val eventPath = watchEvent.context()
                            listeners[eventPath]?.forEach { it(kind, eventPath) }
                        }
                        if (key.reset()) break
                    }
                }
            }
        }
    }


}