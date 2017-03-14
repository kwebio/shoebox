package com.github.sanity.shoebox.demos

import com.github.sanity.shoebox.PersistedMap
import com.github.sanity.shoebox.View
import propheto.mkdirIfAbsent
import java.nio.file.Path
import java.util.*

/**
 * Created by ian on 3/9/17.
 */
class Store(val dir : Path) {
    init {
        dir.mkdirIfAbsent()
    }
    val users = PersistedMap(dir.resolve("users"), User::class)
    val pages = PersistedMap(dir.resolve("pages"), Page::class)

    val estimates =PersistedMap(dir.resolve("estimates"), Estimate::class)

    fun estimatesOf(creatorName: String): View<Estimate> {
        val dir = dir.mkdirIfAbsent()
        return View<Estimate>(dir.resolve("estimates"), estimates, viewBy = {it.p1p2weightRatio.toString()})
    }
}

data class User(val name : String, val nickname : String? = null, val pages : List<String> = Collections.emptyList())

data class Page(val name : String)

data class Estimate(val creatorName : String, val page1 : String, val page2 : String, val p1p2weightRatio : Int)
