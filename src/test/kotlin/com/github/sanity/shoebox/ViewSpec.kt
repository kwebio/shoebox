package com.github.sanity.shoebox

import com.github.sanity.shoebox.Gender.FEMALE
import com.github.sanity.shoebox.Gender.MALE
import io.kotlintest.specs.FreeSpec
import java.lang.AssertionError
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by ian on 3/12/17.
 */
class ViewSpec : FreeSpec() {
    init {
        "on initialization" - {
            val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = {it.gender.toString()})
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
            val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = {it.gender.toString()})

            val addListener = CountingListener("jack", User("Jack", FEMALE))
            viewByGender.onAdd("MALE", addListener::invoke) // Should have no effect
            viewByGender.onAdd("FEMALE", addListener::invoke)

            val removeListener = CountingListener("jack", User("Jack", MALE))
            viewByGender.onRemove("MALE", removeListener::invoke)
            viewByGender.onRemove("FEMALE", removeListener::invoke) // Should have no effect

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
            val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = {it.gender.toString()})
            userMap["jack"] = User("Jack", FEMALE)
            viewByGender.addValue("MALE", "jack")
            viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack")
            viewByGender["MALE"] should beEmpty()
        }

        "should respond to an addition correctly" {
            val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = {it.gender.toString()})
            val addListener = CountingListener("paul", User("Paul", MALE))
            viewByGender.onAdd("MALE", addListener::invoke)
            userMap["paul"] = User("Paul", MALE)
            viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack", "paul")
            viewByGender["MALE"] shouldEqual setOf(User("Paul", MALE), User("Jack", MALE))

            viewByGender.references["FEMALE"]!!.keys shouldEqual setOf("jill")
            viewByGender["FEMALE"] shouldEqual setOf(User("Jill", FEMALE))

            addListener.counter shouldEqual 1
        }

        "should respond to a deletion correctly" {
            val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = {it.gender.toString()})
            val removeListener = CountingListener("jill", User("Jill", FEMALE))
            viewByGender.onRemove("FEMALE", removeListener::invoke)
            userMap.remove("jill")
            viewByGender.references["FEMALE"]!!.keys should beEmpty()
            viewByGender["FEMALE"] should beEmpty()
            viewByGender.references["MALE"]!!.keys shouldEqual setOf("jack")
            viewByGender["MALE"] shouldEqual setOf(User("Jack", MALE))

            removeListener.counter shouldEqual 1
        }
        "should correct for a failure to sync a delete" {
            val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = {it.gender.toString()})
            userMap.remove("jill")
            viewByGender.addValue("FEMALE", "jill")
            viewByGender.references["FEMALE"]!!.keys shouldEqual setOf("jill")
            viewByGender["FEMALE"] should beEmpty()
        }
    }

    class CountingListener<T>(val correctName : String, val correctObject : T?) {
        private val _counter = AtomicInteger(0)

        operator fun invoke(name : String, obj : T?) {
            _counter.incrementAndGet()
            if (name != correctName) throw AssertionError("$name != $correctName")
            if (obj != correctObject) throw AssertionError("$obj != $correctObject")
        }

        val counter get() = _counter.get()
    }
}



data class User(val name : String, val gender : Gender)

enum class Gender {
    MALE, FEMALE
}