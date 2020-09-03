package kweb.shoebox.stores

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.gt
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kweb.shoebox.TestData
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/**
 * Created by ian on 3/22/17.
 */
class DirectoryStoreSpec : FunSpec({
    context("DirectoryStore") {
        context("locking") {
            val dir = Files.createTempDirectory("ss-")
            val directoryStore = DirectoryStore<String>(dir, String.serializer())
            test("should defaultGson a lockfile") {
                Files.exists(dir.resolve("shoebox.lock")) shouldBe true
            }
            test("should throw an exception if attempting to defaultGson a store for a directory that already has a store") {
                shouldThrow<RuntimeException> {
                    DirectoryStore(dir, TestData.serializer())
                }
            }
            test("should disregard an old lock") {
                val dir = Files.createTempDirectory("ss-")
                val lockFilePath = dir.resolve("shoebox.lock")
                Files.newBufferedWriter(lockFilePath).use {
                    it.appendLine("lock")
                }
                Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis() - 60000))
                DirectoryStore<TestData>(dir, TestData.serializer())
            }

            test("should update an old lock") {
                val dir = Files.createTempDirectory("ss-")
                val lockFilePath = dir.resolve("shoebox.lock")
                Files.newBufferedWriter(lockFilePath).use {
                    it.appendLine("lock")
                }
                Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis() - 60000))
                DirectoryStore(dir, TestData.serializer())
                Files.getLastModifiedTime(lockFilePath).toMillis() shouldBe gt(System.currentTimeMillis() - 5000)
            }
        }
        val object1 = TestData(1, 2)
        val object2 = TestData(3, 4)
        context("when an item is stored") {
            val object1 = TestData(1, 2)
            val dir = Files.createTempDirectory("ss-")
            val pm = DirectoryStore(dir, TestData.serializer())
            pm["key1"] = object1
            test("should cache the item that was stored") {
                pm.cache.get("key1").value shouldBe object1
            }
        }
        context("when an item is replaced") {
            val dir = Files.createTempDirectory("ss-")
            val pm = DirectoryStore(dir, TestData.serializer())
            pm["key1"] = object1
            pm["key1"] = object2

            test("should have cached the replaced data") {
                pm.cache.get("key1").value shouldBe object2
            }
            test("should retrieve the replaced data without the cache") {
                pm.cache.invalidate("key1")
                pm["key1"] shouldBe object2
            }
        }
    }
})

