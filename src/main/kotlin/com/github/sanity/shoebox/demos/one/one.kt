package com.github.sanity.shoebox.demos.one

import com.github.sanity.shoebox.Shoebox
import com.github.sanity.shoebox.View
import java.nio.file.Files

/**
 * Created by ian on 3/9/17.
 */

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