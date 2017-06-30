package com.github.sanity.shoebox.stores

import com.github.sanity.shoebox.ShoeboxSpec
import io.kotlintest.matchers.gt
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.FreeSpec
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/**
 * Created by ian on 3/22/17.
 */
class DirectoryStoreSpec : FreeSpec() {
    init {
        "DirectoryStore" - {
            "locking" - {
                val dir = Files.createTempDirectory("ss-")
                val directoryStore = DirectoryStore<String>(dir)
                "should create a lockfile" {
                    Files.exists(dir.resolve("shoebox.lock")) shouldBe true
                }
                "should throw an exception if attempting to create a store for a directory that already has a store" {
                    shouldThrow<RuntimeException> {
                        DirectoryStore<ShoeboxSpec.TestData>(dir)
                    }
                }
                "should disregard an old lock" {
                    val dir = Files.createTempDirectory("ss-")
                    val lockFilePath = dir.resolve("shoebox.lock")
                    Files.newBufferedWriter(lockFilePath).use {
                        it.appendln("lock")
                    }
                    Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis() - 60000))
                    DirectoryStore<ShoeboxSpec.TestData>(dir, ShoeboxSpec.TestData::class)
                }

                "should update an old lock" {
                    val dir = Files.createTempDirectory("ss-")
                    val lockFilePath = dir.resolve("shoebox.lock")
                    Files.newBufferedWriter(lockFilePath).use {
                        it.appendln("lock")
                    }
                    Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis() - 60000))
                    DirectoryStore<ShoeboxSpec.TestData>(dir, ShoeboxSpec.TestData::class)
                    Files.getLastModifiedTime(lockFilePath).toMillis() shouldBe gt (System.currentTimeMillis() - 5000)
                }
            }
            val object1 = ShoeboxSpec.TestData(1, 2)
            val object2 = ShoeboxSpec.TestData(3, 4)
            "when an item is stored" - {
                val object1 = ShoeboxSpec.TestData(1, 2)
                val dir = Files.createTempDirectory("ss-")
                val pm = DirectoryStore<ShoeboxSpec.TestData>(dir)
                pm["key1"] = object1
                "should cache the item that was stored" {
                    pm.cache.get("key1") shouldEqual object1
                }
            }
            "when an item is replaced" - {
                val dir = Files.createTempDirectory("ss-")
                val pm = DirectoryStore<ShoeboxSpec.TestData>(dir)
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
        }
    }
}

