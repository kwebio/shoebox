package kweb.shoebox

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kweb.shoebox.Source.LOCAL
import kweb.shoebox.stores.MemoryStore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by ian on 3/12/17.
 */
class ShoeboxSpec : FunSpec({
    context("A Shoebox store") {
        val object1 = TestData(1, 2)
        val object2 = TestData(3, 4)
        context("when an item is stored") {
            val pm = Shoebox<TestData>(MemoryStore())
            pm["key1"] = object1
            test("should retrieve the data") {
                val retrievedObject: TestData? = pm["key1"]
                retrievedObject shouldBe object1
            }
        }
        context("when an item is removed") {
            val pm = Shoebox<TestData>(MemoryStore())
            pm["key1"] = object1
            pm.remove("key1")
            test("should return null for the removed key") {
                pm["key1"] shouldBe null
            }
        }
        test("should iterate through data") {
            val pm = Shoebox<TestData>(MemoryStore())
            pm["key1"] = TestData(1, 2)
            pm["key2"] = TestData(3, 4)
            val entries = pm.entries
            entries.map { KeyValue(it.key, it.value) }.toSet() shouldBe setOf(KeyValue("key1", TestData(1, 2)), KeyValue("key2", TestData(3, 4)))

        }

        context("should trigger appropriate callbacks when") {
            val object1 = TestData(1, 2)
            val object2 = TestData(3, 4)
            val object3 = TestData(5, 4)
            context("a new object is created") {
                val pm = Shoebox<TestData>(MemoryStore())
                var callCount = AtomicInteger(0)
                val handle: Long = pm.onNew { keyValue, source ->
                    callCount.incrementAndGet() shouldBe 1
                    keyValue shouldBe KeyValue("key1", object1)
                    source shouldBe LOCAL
                }
                pm["key1"] = object1
                test("should trigger callback") { callCount.get() shouldBe 1 }
                pm.deleteNewListener(handle)
                test("should not trigger callback after it has been removed") {
                    callCount.get() shouldBe 1
                    pm["key3"] = object3
                    callCount.get() shouldBe 1
                }
                pm.remove("key3")

            }

            context("an object is changed") {
                val pm = Shoebox<TestData>(MemoryStore())
                pm["key1"] = object1
                var globalCallCount = 0
                var keySpecificCallCount = 0
                val globalChangeHandle = pm.onChange { prev, nextKeyValue, source ->
                    globalCallCount++
                    launch {
                        test("global change callback should be called with the correct parameters") {
                            prev shouldBe object1
                            nextKeyValue shouldBe KeyValue("key1", object2)
                            source shouldBe LOCAL
                        }
                    }
                }
                val keySpecificChangeHandle = pm.onChange("key1") { old, new, source ->
                    keySpecificCallCount++
                    launch {
                        test("key-specific change callback should be called with the correct parameters") {
                            old shouldBe object1
                            new shouldBe object2
                            source shouldBe LOCAL
                        }
                    }
                }
                pm["key1"] = object2
                test("callbacks should each be called once") {
                    globalCallCount shouldBe 1
                    keySpecificCallCount shouldBe 1
                }
                pm["key1"] = object2.copy() // Shouldn't trigger the callbacks again
                test("callbacks shouldn't be called again if the object value hasn't changed") {
                    globalCallCount shouldBe 1
                    keySpecificCallCount shouldBe 1
                }

                pm.deleteChangeListener(globalChangeHandle)
                pm.deleteChangeListener("key1", keySpecificChangeHandle)
                pm["key1"] = object3
                test("callbacks shouldn't be called after they've been removed") {
                    globalCallCount shouldBe 1
                    keySpecificCallCount shouldBe 1
                }


            }
            context("should trigger object removal callback") {
                val pm = Shoebox<TestData>(MemoryStore())
                pm["key1"] = object3
                var callCount = 0
                val onRemoveHandle = pm.onRemove { keyValue, source ->
                    callCount++
                    keyValue shouldBe KeyValue("key1", object3)
                    source shouldBe LOCAL

                }
                pm.remove("key1")
                test("callback should be called once") {
                    callCount shouldBe 1
                }
                pm.deleteRemoveListener(onRemoveHandle)
                pm["key3"] = object3
                pm.remove("key3")
                test("callback shouldn't be called again after it has been removed") {
                    callCount shouldBe 1
                }
            }
        }
    }


})


