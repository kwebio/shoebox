package kweb.shoebox

import io.kotlintest.matchers.*
import io.kotlintest.specs.FreeSpec
import io.kweb.shoebox.data.Gender.*
import io.kweb.shoebox.data.User
import io.kweb.shoebox.stores.MemoryStore

/**
 * Created by ian on 3/14/17.
 */
class OrderedViewSetSpec : FreeSpec() {
    init {
        "an OrderedViewSet" - {

            "on initialization" - {
                val userMap = Shoebox<User>(MemoryStore())
                userMap["zool"] = User("Zool", MALE)
                userMap["george"] = User("George", MALE)
                userMap["paul"] = User("Paul", MALE)
                userMap["xavier"] = User("Xavier", MALE)
                userMap["jack"] = User("Jack", MALE)
                userMap["jill"] = User("Jill", FEMALE)
                val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))
                val menInOrder = listOf(
                        KeyValue("george", User("George", MALE)),
                        KeyValue("jack", User("Jack", MALE)),
                        KeyValue("paul", User("Paul", MALE)),
                        KeyValue("xavier", User("Xavier", MALE)),
                        KeyValue("zool", User("Zool", MALE))
                )
                "keyValueEntries should return men in correct order" {
                    maleViewSet.keyValueEntries shouldEqual menInOrder
                }
                "entries should return men in correct order" {
                    maleViewSet.entries shouldEqual menInOrder.map { it.value }
                }

                val femaleViewSet = OrderedViewSet<User>(viewByGender, "FEMALE", compareBy(User::name))
                femaleViewSet.keyValueEntries shouldEqual listOf(KeyValue("jill", User("Jill", FEMALE)))
            }

            "when a value is added" - {
                val userMap = Shoebox<User>(MemoryStore())
                userMap["jack"] = User("Jack", MALE)
                userMap["paul"] = User("Paul", MALE)
                userMap["jill"] = User("Jill", FEMALE)
                val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                val insertHandle = maleViewSet.onInsert { ix, keyValue ->
                    callCount++
                    "should call the insert handler with the correct values" {
                        callCount shouldBe 1
                        ix shouldBe 2
                        keyValue shouldBe KeyValue("peter", User("Peter", MALE))
                    }
                }
                userMap["peter"] = User("Peter", MALE)
                "should call the insert handler" {
                    callCount shouldBe 1
                }

                "should include newly inserted value in keyValueEntries" {
                    maleViewSet.keyValueEntries shouldEqual listOf(
                            KeyValue("jack", User("Jack", MALE)),
                            KeyValue("paul", User("Paul", MALE)),
                            KeyValue("peter", User("Peter", MALE))
                    )
                }

                "should not call the insert handler after it has been deleted" {
                    maleViewSet.deleteInsertListener(insertHandle)
                    userMap["toby"] = User("Toby", MALE)
                    callCount shouldBe 1
                }
            }

            "when a value is deleted" - {
                val userMap = Shoebox<User>(MemoryStore())
                userMap["jack"] = User("Jack", MALE)
                userMap["paul"] = User("Paul", MALE)
                userMap["jill"] = User("Jill", FEMALE)
                val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                var callCount = 0
                val removeHandle = maleViewSet.onRemove { ix, keyValue ->
                    callCount++
                    "should call the delete handler with the correct values" {
                        callCount shouldBe 1
                        ix shouldBe 0
                        keyValue shouldBe KeyValue("jack", User("Jack", MALE))
                    }
                }
                userMap.remove("jack")
                "should call the remove handler" {
                    callCount shouldBe 1
                }

                "shouldn't include newly removed value in keyValueEntries" {
                    maleViewSet.keyValueEntries shouldEqual listOf(
                            KeyValue("paul", User("Paul", MALE))
                    )
                }

                "should not call the handler after it has been deleted" {
                    maleViewSet.deleteRemoveListener(removeHandle)
                    userMap.remove("paul")
                    callCount shouldBe 1
                }
            }

            " when a second value is added that is not distinguishable based on the supplied comparator" {
                val userMap = Shoebox<User>(MemoryStore())
                userMap["jack"] = User("Jack", MALE)
                userMap["jill"] = User("Jill", FEMALE)
                val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

                val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

                maleViewSet.onInsert { _, kv ->

                }

                userMap["paul"] = User("Paul", MALE)
            }

            "should detect a value reorder" - {
                val userMap = Shoebox<User>(MemoryStore())
                val jackUser = User("Jack", MALE)
                userMap["jack"] = jackUser
                userMap["paul"] = User("Paul", MALE)
                userMap["jill"] = User("Jill", FEMALE)
                val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

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

            "should handle this case discovered while debugging" - {
                data class Dog(val name: String, val color: String, val breed: String)

                val dogs = Shoebox<Dog>()
                listOf(
                        Dog(name = "hot dog", color = "tan", breed = "dachshund"),
                        Dog(name = "toby", color = "tan", breed = "labrador")
                ).forEach { dogs[it.name] = it }

                val viewByColor = dogs.view("dogsByColor", Dog::color)
                val tanDogs = viewByColor.orderedSet("tan", compareBy(Dog::color))
                "dogs should be listed with correct test in correct order" {
                    tanDogs.entries.size shouldBe 2
                    tanDogs.entries[0] shouldBe Dog(name = "hot dog", color = "tan", breed = "dachshund")
                    tanDogs.entries[1] shouldBe Dog(name = "toby", color = "tan", breed = "labrador")
                }
            }
        }
    }
}
