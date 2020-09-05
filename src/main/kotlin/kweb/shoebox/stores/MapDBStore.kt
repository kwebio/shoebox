package kweb.shoebox.stores

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kweb.shoebox.KeyValue
import kweb.shoebox.Store
import org.mapdb.DB
import org.mapdb.HTreeMap
import org.mapdb.Serializer

/**
 * Created by ian on 3/22/17.
 */
@ExperimentalSerializationApi
class MapDBStore<T : Any>(private val db: DB, private val name: String, private val serializer: KSerializer<T>) : Store<T> {
    private val map: HTreeMap<String, ByteArray> = db
            .hashMap(name, Serializer.STRING, Serializer.BYTE_ARRAY)
            .createOrOpen()

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
        val v = map.get(key)
        return if (v == null) {
            null
        } else {
            ProtoBuf.decodeFromByteArray(serializer, v)
        }
    }

    override fun set(key: String, value: T): T? {
        val v = map.get(key)
        map.set(key, ProtoBuf.encodeToByteArray(serializer, value))
        db.commit()
        return if (v == null) {
            null
        } else {
            ProtoBuf.decodeFromByteArray(serializer, v)
        }
    }
}