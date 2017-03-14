package com.github.sanity.shoebox

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Created by ian on 3/12/17.
 */

class FileWatcher() {
    val watchService = FileSystems.getDefault().newWatchService()

    private val listeners = ConcurrentHashMap<Path, MutableList<(WatchEvent.Kind<Path>, Path) -> Unit>>()

    init {
        thread {
            while(true) {
                val watchKey = watchService.take()
                for (event in watchKey.pollEvents()) {
                    val eventPath = (event as WatchEvent<Path>).context()
                    listeners[eventPath]?.forEach { it(event.kind(), eventPath) }
                }
            }
        }
    }

    fun register(path : Path, listener : (WatchEvent.Kind<Path>, Path) -> Unit) {
        listeners.computeIfAbsent(path, {ArrayList()}).add(listener)
    }
}