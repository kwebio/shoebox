package kweb.shoebox

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kweb.shoebox.data.Gender.FEMALE
import kweb.shoebox.data.Gender.MALE
import kweb.shoebox.data.User
import kweb.shoebox.stores.MemoryStore

/**
 * Created by ian on 3/14/17.
 */
class OrderedViewSetSpec : FunSpec({

        context("on initialization") {
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
            test("keyValueEntries should return men in correct order") {
                maleViewSet.keyValueEntries shouldBe menInOrder
            }
            test("entries should return men in correct order") {
                maleViewSet.entries shouldBe menInOrder.map { it.value }
            }

            val femaleViewSet = OrderedViewSet<User>(viewByGender, "FEMALE", compareBy(User::name))
            femaleViewSet.keyValueEntries shouldBe listOf(KeyValue("jill", User("Jill", FEMALE)))
        }

        context("when a value is added") {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["paul"] = User("Paul", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

            val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

            var callCount = 0
            val insertHandle = maleViewSet.onInsert { ix, keyValue ->
                callCount++
                launch {
                    test("should call the insert handler with the correct values") {
                        callCount shouldBe 1
                        ix shouldBe 2
                        keyValue shouldBe KeyValue("peter", User("Peter", MALE))
                    }
                }
            }
            userMap["peter"] = User("Peter", MALE)
            test("should call the insert handler") {
                callCount shouldBe 1
            }

            test("should include newly inserted value in keyValueEntries") {
                maleViewSet.keyValueEntries shouldBe listOf(
                        KeyValue("jack", User("Jack", MALE)),
                        KeyValue("paul", User("Paul", MALE)),
                        KeyValue("peter", User("Peter", MALE))
                )
            }

            test("should not call the insert handler after it has been deleted") {
                maleViewSet.deleteInsertListener(insertHandle)
                userMap["toby"] = User("Toby", MALE)
                callCount shouldBe 1
            }
        }

        context("when a value is deleted") {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["paul"] = User("Paul", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

            val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

            var callCount = 0
            val removeHandle = maleViewSet.onRemove { ix, keyValue ->
                callCount++
                launch {
                    test("should call the delete handler with the correct values") {
                        callCount shouldBe 1
                        ix shouldBe 0
                        keyValue shouldBe KeyValue("jack", User("Jack", MALE))
                    }
                }
            }
            userMap.remove("jack")
            test("should call the remove handler") {
                callCount shouldBe 1
            }

            test("shouldn't include newly removed value in keyValueEntries") {
                maleViewSet.keyValueEntries shouldBe listOf(
                        KeyValue("paul", User("Paul", MALE))
                )
            }

            test("should not call the handler after it has been deleted") {
                maleViewSet.deleteRemoveListener(removeHandle)
                userMap.remove("paul")
                callCount shouldBe 1
            }
        }

        test("when a second value is added that is not distinguishable based on the supplied comparator") {
            val userMap = Shoebox<User>(MemoryStore())
            userMap["jack"] = User("Jack", MALE)
            userMap["jill"] = User("Jill", FEMALE)
            val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

            val maleViewSet = OrderedViewSet<User>(viewByGender, "MALE", compareBy(User::name))

            maleViewSet.onInsert { _, kv ->

            }

            userMap["paul"] = User("Paul", MALE)
        }

        test("should detect a value reorder") {
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
                launch {
                    test("should call the insert handler with the correct values") {
                        ix shouldBe 2
                        keyValue shouldBe KeyValue("jack", renamedJackUser)
                    }
                }
            }
            maleViewSet.onRemove { ix, keyValue ->
                callCount++
                launch {
                    test("should call the remove handler with the correct values") {
                        ix shouldBe 0
                        keyValue shouldBe KeyValue("jack", jackUser)
                    }
                }
            }
            userMap["jack"] = renamedJackUser

            test("should call both handlers") {
                callCount shouldBe 2
            }
        }

        context("should handle this case discovered while debugging") {
            data class Dog(val name: String, val color: String, val breed: String)

            val dogs = Shoebox<Dog>(MemoryStore())
            listOf(
                    Dog(name = "hot dog", color = "tan", breed = "dachshund"),
                    Dog(name = "toby", color = "tan", breed = "labrador")
            ).forEach { dogs[it.name] = it }

            val viewByColor = dogs.view("dogsByColor", Dog::color)
            val tanDogs = viewByColor.orderedSet("tan", compareBy(Dog::color))
            test("dogs should be listed with correct test in correct order") {
                tanDogs.entries.size shouldBe 2
                tanDogs.entries[0] shouldBe Dog(name = "hot dog", color = "tan", breed = "dachshund")
                tanDogs.entries[1] shouldBe Dog(name = "toby", color = "tan", breed = "labrador")
            }
        }

})
