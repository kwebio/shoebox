package com.github.sanity.shoebox

import io.kotlintest.matchers.be
import io.kotlintest.specs.FreeSpec
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by ian on 3/12/17.
 */
internal class PersistedMapSpec : FreeSpec() {
    data class TestData(val one: Int, val two: Int)


    init {
        "A persistent map" - {
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
            "lastModifiedTime" - {
                val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                pm["key2"] = object1
                "should return null for a non-existent key" {
                    pm.lastModifiedTimeMS("key1") shouldEqual null
                }
                "should have correct lastModifiedTime for data" {
                    pm.lastModifiedTimeMS("key2")!! should be gt (System.currentTimeMillis() - 10000)
                    pm.lastModifiedTimeMS("key2")!! should be lt (System.currentTimeMillis() + 10000)
                }
            }
            "should iterate through data" {
                val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                pm["key1"] = TestData(1, 2)
                pm["key2"] = TestData(3, 4)
                pm.all.map { KeyValue(it.key, it.value, 0) }.toSet() shouldEqual setOf(KeyValue("key1", TestData(1, 2), 0), KeyValue("key2", TestData(3, 4), 0))
                forAll(pm.all.toList()) {
                    it.lastModifiedMs should be gt (System.currentTimeMillis() - 10000)
                    it.lastModifiedMs should be lt (System.currentTimeMillis() + 10000)
                }

            }

            "should trigger appropriate callbacks when" - {
                val object1 = TestData(1, 2)
                val object2 = TestData(3, 4)
                val object3 = TestData(5, 4)
                "a new object is created" - {
                    val pm = Store<TestData>(Files.createTempDirectory("ss-"), TestData::class)
                    var callCount = AtomicInteger(0)
                    val handle: Long = pm.onNew { key, obj, locallyInitiated ->
                        callCount.incrementAndGet() shouldEqual 1
                        key shouldEqual "key1"
                        obj shouldEqual object1
                        locallyInitiated shouldEqual true
                    }
                    pm["key1"] = object1
                    "should trigger callback" { callCount.get() shouldEqual 1 }
                    pm.removeNewListener(handle)
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
                    val globalChangeHandle = pm.onChange { key, prev, next, locallyInitiated ->
                        globalCallCount++
                        "global change callback should be called with the correct parameters" {
                            key shouldEqual "key1"
                            prev shouldEqual object1
                            next shouldEqual object2
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

                    pm.removeChangeListener(globalChangeHandle)
                    pm.removeChangeListener("key1", keySpecificChangeHandle)
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
                    val onRemoveHandle = pm.onRemove { name, obj, locallyInitiated ->
                        callCount++
                        name shouldEqual "key1"
                        obj shouldEqual object3
                        locallyInitiated shouldEqual true

                    }
                    pm.remove("key1")
                    "callback should be called once" {
                        callCount shouldEqual 1
                    }
                    pm.removeRemoveListener(onRemoveHandle)
                    pm["key3"] = object3
                    pm.remove("key3")
                    "callback shouldn't be called again after it has been removed" {
                        callCount shouldEqual 1
                    }
                }
            }
        }
    }


    override fun afterAll() {
    }
}