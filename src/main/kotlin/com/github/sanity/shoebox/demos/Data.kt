package com.github.sanity.shoebox.demos

import com.github.sanity.shoebox.Store
import com.github.sanity.shoebox.View
import com.github.sanity.shoebox.mkdirIfAbsent
import java.nio.file.Path

/**
 * Created by ian on 3/9/17.
 */
class Data(val dir : Path) {
    init {
        dir.mkdirIfAbsent()
    }
    val userStore = Store(dir.resolve("usersByUid"), User::class)
    val pageStore = Store(dir.resolve("pages"), Page::class)
    val userPageStore = Store(dir.resolve("userPageStore"), UserPage::class)
    val estimates = Store(dir.resolve("estimates"), Estimate::class)

    val users = Users(this)

    val pages = Pages(this)

    fun estimatesOf(creatorName: String): View<Estimate> {
        val dir = dir.mkdirIfAbsent()
        return View<Estimate>(dir.resolve("estimates"), estimates, viewBy = {it.p1p2weightRatio.toString()})
    }

    class Users(val data: Data) {
        val byEmail = View(data.dir.resolve("usersByNickname"), data.userStore, viewBy = {it.email ?: "unknown"})
        val byPage = View(data.dir.resolve("usersByPage"), data.userPageStore, viewBy = {it.page})
    }

    class Pages(val data: Data) {
        val byUser = View(data.dir.resolve("pagesByUser"), data.userPageStore, viewBy = {it.user})
    }

    data class User(val name : String, val email : String?)

    data class Page(val name : String, val title : String)

    data class UserPage(val user : String, val page : String)

    data class Estimate(val creatorName : String, val page1 : String, val page2 : String, val p1p2weightRatio : Int)

}
