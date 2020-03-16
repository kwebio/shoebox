package kweb.shoebox

import io.kweb.shoebox.data.Gender.FEMALE
import io.kweb.shoebox.data.Gender.MALE
import io.kweb.shoebox.data.User
import io.kweb.shoebox.stores.MemoryStore
import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.FreeSpec
import java.lang.AssertionError
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by ian on 3/12/17.
 */
class ViewSpec : FreeSpec() {
    init {
        "on initialization" - {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
            "references should be correct" {
                viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack")
                viewByGender.references["FEMALE"]!!.keys shouldEqual setOf("jill")
            }
            "should return correctly categorized objects" {
                viewByGender["MALE"] shouldEqual setOf(User("Jack", MALE))
                viewByGender["FEMALE"] shouldEqual setOf(User("Jill", FEMALE))
            }
        }
        "on change of a view name after initialization" - {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

            val addListener = CountingListener<User>(KeyValue("jack", User("Jack", FEMALE)))
            viewByGender.onAdd("MALE", addListener::add) // Should have no effect
            viewByGender.onAdd("FEMALE", addListener::add)

            val removeListener = CountingListener<User>(KeyValue("jack", User("Jack", MALE)))
            viewByGender.onRemove("MALE", removeListener::remove)
            viewByGender.onRemove("FEMALE", removeListener::remove) // Should have no effect

            userMap["jack"] = User("Jack", FEMALE)

            "references should be correct" {
                viewByGender.references["MALE"]!!.keys should beEmpty()
                viewByGender.references["FEMALE"]!!.keys shouldEqual setOf("jack", "jill")

            }
            "actual values returned should be correct" {
                viewByGender["FEMALE"] shouldEqual setOf(User("Jack", FEMALE), User("Jill", FEMALE))
                viewByGender["MALE"] should beEmpty()
            }
            "listeners should have been called" {
                addListener.counter shouldEqual 1
                removeListener.counter shouldEqual 1
            }
        }

        "should respond to a failure to sync a viewName change correctly" {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
            userMap["jack"] = User("Jack", FEMALE)
            viewByGender.addValue("MALE", "jack")
            viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack")
            viewByGender["MALE"] should beEmpty()
        }

        "should respond to an addition correctly" {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
            val addListener = CountingListener<User>(KeyValue("paul", User("Paul", MALE)))
            viewByGender.onAdd("MALE", addListener::add)
            userMap["paul"] = User("Paul", MALE)
            viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack", "paul")
            viewByGender["MALE"] shouldEqual setOf(User("Paul", MALE), User("Jack", MALE))

            viewByGender.references["FEMALE"]!!.keys shouldEqual setOf("jill")
            viewByGender["FEMALE"] shouldEqual setOf(User("Jill", FEMALE))

            addListener.counter shouldEqual 1
        }

        "should respond to a deletion correctly" {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
            val removeListener = CountingListener<User>(KeyValue("jill", User("Jill", FEMALE)))
            viewByGender.onRemove("FEMALE", removeListener::remove)
            userMap.remove("jill")
            viewByGender.references["FEMALE"]!!.keys should beEmpty()
            viewByGender["FEMALE"] should beEmpty()
            viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack")
            viewByGender["MALE"] shouldEqual setOf(User("Jack", MALE))

            removeListener.counter shouldEqual 1
        }
        "should correct for a failure to sync a delete" {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
            userMap.remove("jill")
            viewByGender.addValue("FEMALE", "jill")
            viewByGender.references["FEMALE"]!!.keys shouldEqual setOf("jill")
            viewByGender["FEMALE"] should beEmpty()
        }
    }

    class CountingListener<T>(val correct : KeyValue<in T?>) {
        private val _counter = AtomicInteger(0)

        fun add(kv : KeyValue<T>) {
            _counter.incrementAndGet()
            if (kv != correct) throw AssertionError("$kv != $correct")
        }

        fun remove(kv : KeyValue<T?>) {
            _counter.incrementAndGet()
            if (kv != correct) throw AssertionError("$kv != $correct")
        }

        val counter get() = _counter.get()
    }
}


