package kweb.shoebox.stores

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kweb.shoebox.KeyValue
import kweb.shoebox.Store

/**
 * Created by ian on 3/22/17.
 */
@ExperimentalSerializationApi
class MapStore<T : Any>(private val map : MutableMap<String, ByteArray>, private val serializer: KSerializer<T>) : Store<T> {

    override val entries: Iterable<KeyValue<T>>
        get() = map.map { (k, v) ->
            KeyValue(k, ProtoBuf.decodeFromByteArray(serializer, v))
        }

    override fun remove(key: String): T? {
        val v = map.remove(key)
        return if (v == null) {
            null
        } else {
            ProtoBuf.decodeFromByteArray(serializer, v)
        }
    }

    override fun get(key: String): T? {
        val v = map[key]
        return if (v == null) {
            null
        } else {
            ProtoBuf.decodeFromByteArray(serializer, v)
        }
    }

    override fun set(key: String, value: T): T? {
        val v = map[key]
        map.set(key, ProtoBuf.encodeToByteArray(serializer, value))
        return if (v == null) {
            null
        } else {
            ProtoBuf.decodeFromByteArray(serializer, v)
        }
    }
}