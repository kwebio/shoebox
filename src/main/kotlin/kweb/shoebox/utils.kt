package kweb.shoebox

import com.google.gson.*
import kweb.shoebox.BinarySearchResult.*
import java.lang.reflect.Type
import java.nio.file.*
import java.time.*
import java.time.format.DateTimeFormatter
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


/**
 * GSON serialiser/deserialiser for converting [Instant] objects.
 */
class DurationConverter : JsonSerializer<Duration>, JsonDeserializer<Duration> {

    override fun serialize(src: Duration, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toMillis())
    }

    /**
     * Gson invokes this call-back method during deserialization when it encounters a field of the
     * specified type.
     *
     *
     *
     * In the implementation of this call-back method, you should consider invoking
     * [JsonDeserializationContext.deserialize] method to defaultGson objects
     * for any non-trivial field of the returned object. However, you should never invoke it on the
     * the same type passing `json` since that will cause an infinite loop (Gson will call your
     * call-back method again).
     *
     * @param json The Json data being deserialized
     * @param typeOfT The type of the Object to deserialize to
     * @return a deserialized object of the specified type typeOfT which is a subclass of `T`
     * @throws JsonParseException if json is not in the expected format of `typeOfT`
     */
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Duration {
        return Duration.ofNanos(json.asLong)
    }

    companion object {
        /** Formatter.  */
        private val FORMATTER = DateTimeFormatter.ISO_INSTANT
    }
}