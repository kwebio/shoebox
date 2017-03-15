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
* Data is stored as JSON in ordinary directories

### Limitations
* Doesn't implement the MutableMap interface
  * This is because some MutableMap operations would require loading the entire Map into RAM
* Not very space efficient for small objects as files take up at least 4K on many filesystems
* Directories can't be shared between different Shoebox instances yet, although this is planned
  * Once supported we should be able to use [shared filesystems](https://aws.amazon.com/blogs/aws/amazon-elastic-file-system-shared-file-storage-for-amazon-ec2/)
    to scale horizontally, limited only by the filesystem's scalability

### Usage Example
```kotlin
fun main(args : Array<String>) {
    val dir = Files.createTempDirectory("sb-")
    val userStore = Store(dir.resolve("users"), User::class)
    val usersByEmail = View(dir.resolve("usersByEmail"), userStore, viewBy = User::email)
    val usersByGender = View(dir.resolve("usersByGender"), userStore, viewBy = User::gender)

    userStore["ian"] = User("Ian Clarke", "male", "ian@blah.com")
    userStore["fred"] = User("Fred Smith", "male", "fred@blah.com")
    userStore["sue"] = User("Sue Smith", "female", "fred@blah.com")

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
```

### Documentation
* API
  * [0.1.3](https://jitpack.io/com/github/sanity/shoebox/0.1.3/javadoc/)
  * [SNAPSHOT](https://jitpack.io/com/github/sanity/shoebox/-SNAPSHOT/javadoc/) (might be slow to load)
