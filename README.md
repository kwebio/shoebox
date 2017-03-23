# ShoeBox

[![Build Status](https://travis-ci.org/sanity/shoebox.svg?branch=master)](https://travis-ci.org/sanity/shoebox)

ShoeBox is a [Kotlin](http://kotlinlang.org/) library for object persistence that supports the 
[observer pattern](https://en.wikipedia.org/wiki/Observer_pattern) so your code can be notified immediately when 
stored data is changed.

### Motivation

While it is a standalone library, ShoeBox was created as a persistence layer for [Kweb](http://kweb.io/) applications, 
motivated by the lack of a simple persistence mechanism that supports the observer pattern.  The idea is to create a 
"bridge" library between Shoebox and Kweb that will allow "binding" of UI components to persistent state, also known as 
the [data mapper pattern](https://en.m.wikipedia.org/wiki/Data_mapper_pattern).
[Here is a video](https://www.youtube.com/watch?v=0Q-BUldFZjA) illustrating this idea for TornadoFX on Android.

To emphasize, however, Shoebox doesn't depend on Kweb and should be useful for many other things.

### Features
* Functionality similar to MutableMap
* Add listeners for object addition, deletion, and modification
* Fairly comprehensive [unit tests](https://github.com/sanity/shoebox/tree/master/src/test/kotlin/com/github/sanity/shoebox)
* Lightweight, pulls in very few dependencies
* Materialized views
  * Efficiently retrieve objects by any specified key derived from the object
  * Similar to database indexes, but also supporting the observer pattern
* Currently data is stored as uncompressed files, serialized to JSON

### Limitations
* Not very space efficient for small objects as files take up at least 4K on many filesystems
* Data isn't currently compressed
* Directories can't be shared between different Shoebox instances, although this is planned
  * Once this is supported we can use [shared filesystems](https://aws.amazon.com/blogs/aws/amazon-elastic-file-system-shared-file-storage-for-amazon-ec2/)
    to scale horizontally, limited only by the filesystem
* Doesn't implement the [MutableMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/) interface
  * Even though the semantics are very similar to MutableMap, it isn't currently implemented because we wanted to avoid
    loading all data into memory.  
  * This is probably solvable through custom implementations of MutableSet, MutableEntry and other interfaces, and
    will probably be done before 1.0 is released.

### Adding to your project
Shoebox can be added easily to your Maven or Gradle project through Jitpack:

[![Release](https://jitpack.io/v/sanity/shoebox.svg)](https://jitpack.io/#sanity/shoebox)

### Usage Example
```kotlin
fun main(args : Array<String>) {
    val dir = Files.createTempDirectory("sb-")
    val userStore = Shoebox<User>(dir.resolve("users"))
    val usersByEmail = View(Shoebox(dir.resolve("usersByEmail")), userStore, viewBy = User::email)
    val usersByGender = View(Shoebox(dir.resolve("usersByGender")), userStore, viewBy = User::gender)

    userStore["ian"] = User("Ian Clarke", "male", "ian@blah.com")
    userStore["fred"] = User("Fred Smith", "male", "fred@blah.com")
    userStore["sue"] = User("Sue Smith", "female", "sue@blah.com")

    println(usersByEmail["ian@blah.com"])   // [User(name=Ian Clarke, gender=male, email=ian@blah.com)]
    println(usersByGender["male"])          // [User(name=Ian Clarke, gender=male, email=ian@blah.com),
                                            // User(name=Fred Smith, gender=male, email=fred@blah.com)]
    // note: view["xx]" returns a set of values
    usersByGender.onAdd("male", {kv ->
        println("${kv.key} became male")
    })
    usersByGender.onRemove("male", {kv ->
        println("${kv.key} ceased to be male")
    })

    userStore["fred"] = userStore["fred"]!!.copy(gender = "female") // Prints "fred ceased to be male"
}

data class User(val name : String, val gender : String, val email : String)

data class User(val name : String, val gender : String, val email : String)
```

### Documentation
* API
  * [0.2.0](https://jitpack.io/com/github/sanity/shoebox/0.2.0g/javadoc/)
  * [SNAPSHOT](https://jitpack.io/com/github/sanity/shoebox/-SNAPSHOT/javadoc/) (might be slow to load)
