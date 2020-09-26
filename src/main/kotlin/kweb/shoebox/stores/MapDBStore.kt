package kweb.shoebox.stores

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kweb.shoebox.KeyValue
import kweb.shoebox.Store
import org.mapdb.DB
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import kotlin.reflect.KClass

/**
 * Created by ian on 3/22/17.
 */
@ExperimentalSerializationApi
class MapDBStore<T : Any>(val db: DB, val name: String, val serializer: KSerializer<T>, val format: Format<Any>) : Store<T> {

    private val map: HTreeMap<String, Any> =
            db
                    .hashMap(name)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(when(format.format) {
                        String::class -> Serializer.STRING
                        ByteArray::class -> Serializer.BYTE_ARRAY
                        else -> error("Only String and ByteArray serialization formats are supported, ${format.format} is not.")
                    })
                    .createOrOpen() as HTreeMap<String, Any>

    override val entries: Iterable<KeyValue<T>>
        get() = map.map { (k, v) ->
            KeyValue(k, format.deserialize(serializer, v))
        }

    override fun remove(key: String): T? {
        val v = map.remove(key)
        return if (v == null) {
            null
        } else {
            format.deserialize(serializer, v)
        }
    }

    override fun get(key: String): T? {
        val v = map[key]
        return if (v == null) {
            null
        } else {
            Json.decodeFromString(serializer, v)
        }
    }

    override fun set(key: String, value: T): T? {
        val v = map[key]
        map.set(key, format.serialize(serializer, value) as Any)
        return if (v == null) {
            null
        } else {
            Json.decodeFromString(serializer, v)
        }
    }

    interface Format<O : Any> {
        val format: KClass<O>

        fun <T : Any> serialize(serializer: KSerializer<T>, value: T): O

        fun <T : Any> deserialize(serializer: KSerializer<T>, data: O): T
    }

    class JsonFormat : Format<String> {
        override val format = String::class

        override fun <T : Any> serialize(serializer: KSerializer<T>, value: T): String = Json.encodeToString(serializer, value)

        override fun <T : Any> deserialize(serializer: KSerializer<T>, data: String): T = Json.decodeFromString(serializer, data)
    }

    class ProtoBufFormat : Format<ByteArray> {
        private val protoBuf = ProtoBuf { encodeDefaults = false }

        override val format = ByteArray::class

        override fun <T : Any> serialize(serializer: KSerializer<T>, value: T): ByteArray = protoBuf.encodeToByteArray(serializer, value)

        override fun <T : Any> deserialize(serializer: KSerializer<T>, data: ByteArray): T =protoBuf.decodeFromByteArray(serializer, data)
    }
}

fun main() {
    @Serializable
    data class Bar(val k: Int? = null)

    val s = ProtoBuf { encodeDefaults = false }.encodeToByteArray(Bar.serializer(), Bar(null))
}