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
data class User(val name : String, val email : String)
data class Page(val name : String, val title : String)
data class UserPage(val user : String, val page : String)

val userStore = Store(dir.resolve("users"), User::class)
val pageStore = Store(dir.resolve("pages"), Page::class)
val userPageStore = Store(dir.resolve("userPages"), UserPage::class)

val usersByEmail = View(data.dir.resolve("usersByEmail"), data.userStore, viewBy = User::email)

fun main(args : Array<String>) { 
    userStore["ian"] = User("Ian Clarke", "ian@blah.com")
    userStore["fred"] = User("Fred Smith", "fred@blah.com")
    println(usersByEmail["ian@blah.com"]) // Will print setOf(User("Ian Clarke", "ian@blah.com"))
    
}
```

### Documentation
* API
  * [0.1.3](https://jitpack.io/com/github/sanity/shoebox/0.1.3/javadoc/)
  * [SNAPSHOT](https://jitpack.io/com/github/sanity/shoebox/-SNAPSHOT/javadoc/) (might be slow to load)
