package com.github.sanity.shoebox

import com.github.sanity.shoebox.filebased.Store
import com.github.sanity.shoebox.KeyValue
import io.kotlintest.specs.FreeSpec
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by ian on 3/12/17.
 */
class StoreSpec : FreeSpec() {


    init {
        "A Shoebox store" - {
            val object1 = TestData(1, 2)
            val object2 = TestData(3, 4)
            "when an item is stored" - {
                val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                pm["key1"] = object1
                "should cache the item that was stored" {
                    pm.cache.get("key1") shouldEqual object1
                }
                "should retrieve the data" {
                    val retrievedObject: TestData? = pm["key1"]
                    retrievedObject shouldEqual object1
                }
                "should retrieve the data without it being cached" {
                    pm.cache.invalidate("key1")
                    val retrievedObject: TestData? = pm["key1"]
                    retrievedObject shouldEqual object1
                    pm.cache.get("key1") shouldEqual object1
                }
            }
            "when an item is replaced" - {
                val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                pm["key1"] = object1
                pm["key1"] = object2

                "should have cached the replaced data" {
                    pm.cache.get("key1") shouldEqual object2
                }
                "should retrieve the replaced data without the cache" {
                    pm.cache.invalidate("key1")
                    pm["key1"] shouldEqual object2
                }
            }
            "when an item is removed" - {
                val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                pm["key1"] = object1
                pm.remove("key1")
                "should return null for the removed key" {
                    pm["key1"] shouldEqual null
                }
            }
            "should iterate through data" {
                val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                pm["key1"] = TestData(1, 2)
                pm["key2"] = TestData(3, 4)
                pm.entries.map { KeyValue(it.key, it.value) }.toSet() shouldEqual setOf(KeyValue("key1", TestData(1, 2)), KeyValue("key2", TestData(3, 4)))

            }

            "should trigger appropriate callbacks when" - {
                val object1 = TestData(1, 2)
                val object2 = TestData(3, 4)
                val object3 = TestData(5, 4)
                "a new object is created" - {
                    val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                    var callCount = AtomicInteger(0)
                    val handle: Long = pm.onNew { keyValue, locallyInitiated ->
                        callCount.incrementAndGet() shouldEqual 1
                        keyValue shouldEqual KeyValue("key1", object1)
                        locallyInitiated shouldEqual true
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
                    val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                    pm["key1"] = object1
                    var globalCallCount = 0
                    var keySpecificCallCount = 0
                    val globalChangeHandle = pm.onChange { prev, nextKeyValue, locallyInitiated ->
                        globalCallCount++
                        "global change callback should be called with the correct parameters" {
                            prev shouldEqual object1
                            nextKeyValue shouldEqual KeyValue("key1", object2)
                            locallyInitiated shouldEqual true
                        }
                    }
                    val keySpecificChangeHandle = pm.onChange("key1") { old, new, locallyInitiated ->
                        keySpecificCallCount++
                        "key-specific change callback should be called with the correct parameters" {
                            old shouldEqual object1
                            new shouldEqual object2
                            locallyInitiated shouldBe true
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
                    val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                    pm["key1"] = object3
                    var callCount = 0
                    val onRemoveHandle = pm.onRemove { keyValue, locallyInitiated ->
                        callCount++
                        keyValue shouldEqual KeyValue("key1", object3)
                        locallyInitiated shouldEqual true

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

    internal data class TestData(val one: Int, val two: Int)

    override fun afterAll() {
    }
}