package kweb.shoebox

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kweb.shoebox.data.Gender.FEMALE
import kweb.shoebox.data.Gender.MALE
import kweb.shoebox.data.User
import kweb.shoebox.stores.MemoryStore

/**
 * Created by ian on 3/12/17.
 */
class ViewSpec : FunSpec({
    context("on initialization") {
        val userMap = Shoebox<User>(MemoryStore())
        userMap["jack"] = User("Jack", MALE)
        userMap["jill"] = User("Jill", FEMALE)
        val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
        test("references should be correct") {
            viewByGender.references["MALE"]!!.keys shouldBe setOf("jack")
            viewByGender.references["FEMALE"]!!.keys shouldBe setOf("jill")
        }
        test("should return correctly categorized objects") {
            viewByGender["MALE"] shouldBe setOf(User("Jack", MALE))
            viewByGender["FEMALE"] shouldBe setOf(User("Jill", FEMALE))
        }
    }
    context("on change of a view name after initialization") {
        val userMap = Shoebox<User>(MemoryStore())
        userMap["jack"] = User("Jack", MALE)
        userMap["jill"] = User("Jill", FEMALE)
        val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })

        val addListener = CountingListener<User>(KeyValue("jack", User("Jack", FEMALE)))
        viewByGender.onAdd("MALE", addListener::add) // Should have no effect
        viewByGender.onAdd("FEMALE", addListener::add)

        val removeListener = CountingListener<User>(KeyValue("jack", User("Jack", MALE)))
        viewByGender.onRemove("MALE", removeListener::remove)
        viewByGender.onRemove("FEMALE", removeListener::remove) // Should have no effect

        userMap["jack"] = User("Jack", FEMALE)

        test("references should be correct") {
            viewByGender.references["MALE"]!!.keys shouldBe emptySet()
            viewByGender.references["FEMALE"]!!.keys shouldBe setOf("jack", "jill")

        }
        test("actual values returned should be correct") {
            viewByGender["FEMALE"] shouldBe setOf(User("Jack", FEMALE), User("Jill", FEMALE))
            viewByGender["MALE"] shouldBe emptySet()
        }
        test("listeners should have been called") {
            addListener.counter shouldBe 1
            removeListener.counter shouldBe 1
        }
    }

    test("should respond to a failure to sync a viewName change correctly") {
        val userMap = Shoebox<User>(MemoryStore())
        userMap["jack"] = User("Jack", MALE)
        userMap["jill"] = User("Jill", FEMALE)
        val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
        userMap["jack"] = User("Jack", FEMALE)
        viewByGender.addValue("MALE", "jack")
        viewByGender.references["MALE"]!!.keys shouldBe setOf("jack")
        viewByGender["MALE"] shouldBe emptySet()
    }

    test("should respond to an addition correctly") {
        val userMap = Shoebox<User>(MemoryStore())
        userMap["jack"] = User("Jack", MALE)
        userMap["jill"] = User("Jill", FEMALE)
        val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
        val addListener = CountingListener<User>(KeyValue("paul", User("Paul", MALE)))
        viewByGender.onAdd("MALE", addListener::add)
        userMap["paul"] = User("Paul", MALE)
        viewByGender.references["MALE"]!!.keys shouldBe setOf("jack", "paul")
        viewByGender["MALE"] shouldBe setOf(User("Paul", MALE), User("Jack", MALE))

        viewByGender.references["FEMALE"]!!.keys shouldBe setOf("jill")
        viewByGender["FEMALE"] shouldBe setOf(User("Jill", FEMALE))

        addListener.counter shouldBe 1
    }

    test("should respond to a deletion correctly") {
        val userMap = Shoebox<User>(MemoryStore())
        userMap["jack"] = User("Jack", MALE)
        userMap["jill"] = User("Jill", FEMALE)
        val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
        val removeListener = CountingListener<User>(KeyValue("jill", User("Jill", FEMALE)))
        viewByGender.onRemove("FEMALE", removeListener::remove)
        userMap.remove("jill")
        viewByGender.references["FEMALE"]!!.keys shouldBe emptySet()
        viewByGender["FEMALE"] shouldBe emptySet()
        viewByGender.references["MALE"]!!.keys shouldBe setOf("jack")
        viewByGender["MALE"] shouldBe setOf(User("Jack", MALE))

        removeListener.counter shouldBe 1
    }
    test("should correct for a failure to sync a delete") {
        val userMap = Shoebox<User>(MemoryStore())
        userMap["jack"] = User("Jack", MALE)
        userMap["jill"] = User("Jill", FEMALE)
        val viewByGender = View(Shoebox(MemoryStore()), viewOf = userMap, viewBy = { it.gender.toString() })
        userMap.remove("jill")
        viewByGender.addValue("FEMALE", "jill")
        viewByGender.references["FEMALE"]!!.keys shouldBe setOf("jill")
        viewByGender["FEMALE"] shouldBe emptySet()
    }


})


