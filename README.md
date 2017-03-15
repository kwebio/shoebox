# ShoeBox

[![Build Status](https://travis-ci.org/sanity/shoebox.svg?branch=master)](https://travis-ci.org/sanity/shoebox)

ShoeBox is a [Kotlin](http://kotlinlang.org/) library for object persistence that supports change observers.

ShoeBox was created as a simple persistence layer for [Kweb](http://kweb.io/) applications, motivated primarily by
the lack of simple peristence mechanisms that support observation.

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

### Documentation
* API
  * [0.1.2](https://jitpack.io/com/github/sanity/shoebox/0.1.2/javadoc/)
  * [SNAPSHOT](https://jitpack.io/com/github/sanity/shoebox/-SNAPSHOT/javadoc/) (might be slow to load)
