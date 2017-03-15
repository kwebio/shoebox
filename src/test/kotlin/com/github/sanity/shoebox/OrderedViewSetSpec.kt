package com.github.sanity.shoebox

import com.github.sanity.shoebox.data.Gender
import com.github.sanity.shoebox.data.User
import io.kotlintest.specs.FreeSpec
import java.nio.file.Files

/**
 * Created by ian on 3/14/17.
 */
class OrderedViewSetSpec : FreeSpec() {
    init {
        "an OrderedViewSet" - {

            "on initialization" - {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["zool"] = User("Zool", Gender.MALE)
                userMap["george"] = User("George", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["xavier"] = User("Xavier", Gender.MALE)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))
                val menInOrder = listOf(
                        KeyValue("george", User("George", Gender.MALE)),
                        KeyValue("jack", User("Jack", Gender.MALE)),
                        KeyValue("paul", User("Paul", Gender.MALE)),
                        KeyValue("xavier", User("Xavier", Gender.MALE)),
                        KeyValue("zool", User("Zool", Gender.MALE))
                )
                "keyValueEntries should return men in correct order" {
                    maleViewSet.keyValueEntries shouldEqual menInOrder
                }
                "entries should return men in correct order" {
                    maleViewSet.entries shouldEqual menInOrder.map { it.value }
                }

                val femaleViewSet = OrderedViewSet<User>(viewByGender, "FEMALE", compareBy(User::name))
                femaleViewSet.keyValueEntries shouldEqual listOf(KeyValue("jill", User("Jill", Gender.FEMALE)))
            }

            "when a value is added" - {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                val insertHandle = maleViewSet.onInsert { ix, keyValue ->
                    callCount++
                    "should call the insert handler with the correct values" {
                        callCount shouldBe 1
                        ix shouldBe 2
                        keyValue shouldBe KeyValue("peter", User("Peter", Gender.MALE))
                    }
                }
                userMap["peter"] = User("Peter", Gender.MALE)
                "should call the insert handler" {
                    callCount shouldBe 1
                }

                "should include newly inserted value in keyValueEntries" {
                    maleViewSet.keyValueEntries shouldEqual listOf(
                            KeyValue("jack", User("Jack", Gender.MALE)),
                            KeyValue("paul", User("Paul", Gender.MALE)),
                            KeyValue("peter", User("Peter", Gender.MALE))
                    )
                }

                "should not call the insert handler after it has been deleted" {
                    maleViewSet.deleteInsertListener(insertHandle)
                    userMap["toby"] = User("Toby", Gender.MALE)
                    callCount shouldBe 1
                }
            }

            "when a value is deleted" - {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                val removeHandle = maleViewSet.onRemove { ix, keyValue ->
                    callCount++
                    "should call the delete handler with the correct values" {
                        callCount shouldBe 1
                        ix shouldBe 0
                        keyValue shouldBe KeyValue("jack", User("Jack", Gender.MALE))
                    }
                }
                userMap.remove("jack")
                "should call the remove handler" {
                    callCount shouldBe 1
                }

                "shouldn't include newly removed value in keyValueEntries" {
                    maleViewSet.keyValueEntries shouldEqual listOf(
                            KeyValue("paul", User("Paul", Gender.MALE))
                    )
                }

                "should not call the handler after it has been deleted" {
                    maleViewSet.deleteRemoveListener(removeHandle)
                    userMap.remove("paul")
                    callCount shouldBe 1
                }
            }

            "should detect a value reorder" - {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                val jackUser = User("Jack", Gender.MALE)
                userMap["jack"] = jackUser
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                val renamedJackUser = jackUser.copy(name = "Zeus")
                maleViewSet.onInsert { ix, keyValue ->
                    callCount++
                    "should call the insert handler with the correct values" {
                        ix shouldBe 2
                        keyValue shouldBe KeyValue("jack", renamedJackUser)
                    }
                }
                maleViewSet.onRemove { ix, keyValue ->
                    callCount++
                    "should call the remove handler with the correct values" {
                        ix shouldBe 0
                        keyValue shouldBe KeyValue("jack", jackUser)
                    }
                }
                userMap["jack"] = renamedJackUser

                "should call both handlers" {
                    callCount shouldBe 2
                }
            }
        }
    }
}
