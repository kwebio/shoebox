# ShoeBox

[![Build Status](https://travis-ci.org/sanity/shoebox.svg?branch=master)](https://travis-ci.org/sanity/shoebox)

ShoeBox is a [Kotlin](http://kotlinlang.org/) library for object persistence that supports change observers.

ShoeBox was created as a simple persistence layer for [Kweb](http://kweb.io/) applications, motivated by
the lack of simple peristence mechanisms that support observation.  However, ShoeBox is standalone and has no 
dependency on Kweb.

### Features
* Semantics similar to MutableMap
* Add listeners for object addition, deletion, and modification
* Fairly comprehensive unit tests
* Add views, which can index objects by any computed value, and which will stay in sync automatically
* Views also support change modifications

### Limitations
* Doesn't implement the MutableMap interface
  * This is because some MutableMap operations would require loading the entire Map into RAM
* Uses the filesystem for persistent storage, although alternate back-ends can be supported in future

### Usage Example
```kotlin
data class User(val name : String, val gender : String, val email : String)

val dir = Paths.get(".")

val userStore = Store(dir.resolve("users"), User::class)
val usersByEmail = View(dir.resolve("usersByEmail"), userStore, viewBy = User::email)
val usersByGender = View(dir.resolve("usersByGender"), userStore, viewBy = User::gender)

fun main(args : Array<String>) {
    userStore["ian"] = User("Ian Clarke", "male", "ian@blah.com")
    val fredUser = User("Fred Smith", "male", "fred@blah.com")
    userStore["fred"] = fredUser
    userStore["sue"] = User("Sue Smith", "female", "fred@blah.com")

    println(usersByEmail["ian@blah.com"])   // Will print setOf(User("Ian Clarke", "ian@blah.com"))
    println(usersByGender["male"])          // Will print setOf(User("Ian Clarke", "ian@blah.com"),
                                            //                  User("Fred Smith", "male", "fred@blah.com"))

    usersByGender.onAdd("male", {kv ->
        println("${kv.key} became male")
    })
    usersByGender.onRemove("male", {kv ->
        println("${kv.key} ceased to be male")
    })

    userStore["fred"] = fredUser.copy(gender = "female") // Will print "fred ceased to be male"
}
```

### Documentation
* API
  * [0.1.3](https://jitpack.io/com/github/sanity/shoebox/0.1.3/javadoc/)
  * [SNAPSHOT](https://jitpack.io/com/github/sanity/shoebox/-SNAPSHOT/javadoc/) (might be slow to load)
