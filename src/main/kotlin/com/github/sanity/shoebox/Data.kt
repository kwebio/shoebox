package com.github.sanity.shoebox

import propheto.mkdirIfAbsent
import java.nio.file.Path
import java.util.*

/**
 * Created by ian on 3/9/17.
 */
class Store(val dir : Path) {
    val users get() = PersistedMap(dir.mkdirIfAbsent().resolve("users"), User::class)


}

data class User(val name : String, val nickname : String? = null, val pages : List<String> = Collections.emptyList())

data class Page(val name : String)