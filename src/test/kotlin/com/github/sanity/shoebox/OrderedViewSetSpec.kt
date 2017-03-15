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

            "should be initialized with the correct values in the correct order" {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))
                maleViewSet.keyValueEntries shouldEqual listOf(KeyValue("jack", User("Jack", Gender.MALE)), KeyValue("paul", User("Paul", Gender.MALE)))

                val femaleViewSet = OrderedViewSet<User>(viewByGender, "FEMALE", compareBy(User::name))
                femaleViewSet.keyValueEntries shouldEqual listOf(KeyValue("jill", User("Jill", Gender.FEMALE)))
            }

            "should detect a value addition" {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                maleViewSet.onInsert { ix, keyValue ->
                    callCount++
                    callCount shouldBe 1
                    ix shouldBe 2
                    keyValue shouldBe KeyValue("peter", User("Peter", Gender.MALE))
                }
                userMap["peter"] = User("Peter", Gender.MALE)
                callCount shouldBe 1
            }

            "should detect a value deletion" {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                maleViewSet.onRemove { ix, keyValue ->
                    callCount++
                    callCount shouldBe 1
                    ix shouldBe 0
                    keyValue shouldBe KeyValue("jack", User("Jack", Gender.MALE))
                }
                userMap.remove("jack")
                callCount shouldBe 1
            }

            "should detect a value reorder" {
                val userMap = Store<User>(Files.createTempDirectory("ss-"), User::class)
                userMap["jack"] = User("Jack", Gender.MALE)
                userMap["paul"] = User("Paul", Gender.MALE)
                userMap["jill"] = User("Jill", Gender.FEMALE)
                val viewByGender = View(Files.createTempDirectory("ss-"), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                maleViewSet.onInsert { ix, keyValue ->
                    callCount++
                    ix shouldBe 2
                    keyValue shouldBe KeyValue("jack", User("Zeus", Gender.MALE))
                }
                maleViewSet.onRemove { ix, keyValue ->
                    callCount++
                    ix shouldBe 0
                    keyValue shouldBe KeyValue("jack", User("Jack", Gender.MALE))
                }
                userMap["jack"] = User("Zeus", Gender.MALE)

                callCount shouldBe 2
            }
        }
    }
}
