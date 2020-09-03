package kweb.shoebox

import java.util.concurrent.atomic.AtomicInteger

class CountingListener<T>(val correct: KeyValue<in T?>) {
    private val _counter = AtomicInteger(0)

    fun add(kv: KeyValue<T>) {
        _counter.incrementAndGet()
        if (kv != correct) throw AssertionError("$kv != $correct")
    }

    fun remove(kv: KeyValue<T?>) {
        _counter.incrementAndGet()
        if (kv != correct) throw AssertionError("$kv != $correct")
    }

    val counter get() = _counter.get()
}