package com.github.sanity.shoebox

import com.github.sanity.shoebox.stores.MemoryStore
import io.kotlintest.specs.FreeSpec
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by ian on 3/12/17.
 */
class ShoeboxSpec : FreeSpec() {


    init {
        "A Shoebox store" - {
            val object1 = TestData(1, 2)
            val object2 = TestData(3, 4)
            "when an item is stored" - {
                val pm = Shoebox<TestData>(MemoryStore())
                pm["key1"] = object1
                "should retrieve the data" {
                    val retrievedObject: TestData? = pm["key1"]
                    retrievedObject shouldEqual object1
                }
            }
            "when an item is removed" - {
                val pm = Shoebox<TestData>(MemoryStore())
                pm["key1"] = object1
                pm.remove("key1")
                "should return null for the removed key" {
                    pm["key1"] shouldEqual null
                }
            }
            "should iterate through data" {
                val pm = Shoebox<TestData>(MemoryStore())
                pm["key1"] = TestData(1, 2)
                pm["key2"] = TestData(3, 4)
                val entries = pm.entries
                entries.map { KeyValue(it.key, it.value) }.toSet() shouldEqual setOf(KeyValue("key1", TestData(1, 2)), KeyValue("key2", TestData(3, 4)))

            }

            "should trigger appropriate callbacks when" - {
                val object1 = TestData(1, 2)
                val object2 = TestData(3, 4)
                val object3 = TestData(5, 4)
                "a new object is created" - {
                    val pm = Shoebox<TestData>(MemoryStore())
                    var callCount = AtomicInteger(0)
                    val handle: Long = pm.onNew { keyValue, source ->
                        callCount.incrementAndGet() shouldEqual 1
                        keyValue shouldEqual KeyValue("key1", object1)
                        source shouldEqual Source.LOCAL
                    }
                    pm["key1"] = object1
                    "should trigger callback" { callCount.get() shouldEqual 1 }
                    pm.deleteNewListener(handle)
                    "should not trigger callback after it has been removed" {
                        callCount.get() shouldEqual 1
                        pm["key3"] = object3
                        callCount.get() shouldEqual 1
                    }
                    pm.remove("key3")

                }
                "an object is changed" - {
                    val pm = Shoebox<TestData>(MemoryStore())
                    pm["key1"] = object1
                    var globalCallCount = 0
                    var keySpecificCallCount = 0
                    val globalChangeHandle = pm.onChange { prev, nextKeyValue, source ->
                        globalCallCount++
                        "global change callback should be called with the correct parameters" {
                            prev shouldEqual object1
                            nextKeyValue shouldEqual KeyValue("key1", object2)
                            source shouldEqual Source.LOCAL
                        }
                    }
                    val keySpecificChangeHandle = pm.onChange("key1") { old, new, source ->
                        keySpecificCallCount++
                        "key-specific change callback should be called with the correct parameters" {
                            old shouldEqual object1
                            new shouldEqual object2
                            source shouldBe Source.LOCAL
                        }
                    }
                    pm["key1"] = object2
                    "callbacks should each be called once" {
                        globalCallCount shouldEqual 1
                        keySpecificCallCount shouldEqual 1
                    }
                    pm["key1"] = object2.copy() // Shouldn't trigger the callbacks again
                    "callbacks shouldn't be called again if the object value hasn't changed" {
                        globalCallCount shouldEqual 1
                        keySpecificCallCount shouldEqual 1
                    }

                    pm.deleteChangeListener(globalChangeHandle)
                    pm.deleteChangeListener("key1", keySpecificChangeHandle)
                    pm["key1"] = object3
                    "callbacks shouldn't be called after they've been removed" {
                        globalCallCount shouldEqual 1
                        keySpecificCallCount shouldEqual 1
                    }


                }
                "should trigger object removal callback" - {
                    val pm = Shoebox<TestData>(MemoryStore())
                    pm["key1"] = object3
                    var callCount = 0
                    val onRemoveHandle = pm.onRemove { keyValue, source ->
                        callCount++
                        keyValue shouldEqual KeyValue("key1", object3)
                        source shouldEqual Source.LOCAL

                    }
                    pm.remove("key1")
                    "callback should be called once" {
                        callCount shouldEqual 1
                    }
                    pm.deleteRemoveListener(onRemoveHandle)
                    pm["key3"] = object3
                    pm.remove("key3")
                    "callback shouldn't be called again after it has been removed" {
                        callCount shouldEqual 1
                    }
                }
            }
        }
    }

    data class TestData(val one: Int, val two: Int)

    override fun afterAll() {
    }
}