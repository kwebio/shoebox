package kweb.shoebox.stores

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kweb.shoebox.KeyValue
import kweb.shoebox.Store
import org.mapdb.DB
import org.mapdb.Serializer

/**
 * Created by ian on 3/22/17.
 */
@ExperimentalSerializationApi
class MapDBStore<T : Any>(val db : DB, val name : String, val serializer: KSerializer<T>) : Store<T> {

    val map =
            db
                    .hashMap(name)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen()

    override val entries: Iterable<KeyValue<T>>
        get() = map.map { (k, v) ->
            KeyValue(k, Json.decodeFromString(serializer, v))
        }

    override fun remove(key: String): T? {
        val v = map.remove(key)
        return if (v == null) {
            null
        } else {
            Json.decodeFromString(serializer, v)
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
        map.set(key, Json.encodeToString(serializer, value))
        return if (v == null) {
            null
        } else {
            Json.decodeFromString(serializer, v)
        }
    }
}
