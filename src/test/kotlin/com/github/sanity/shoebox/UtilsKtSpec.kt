package com.github.sanity.shoebox

import io.kotlintest.specs.FreeSpec

/**
 * Created by ian on 3/12/17.
 */
class UtilsKtSpec : FreeSpec() {
    init {
        "identityRemove should" - {
            val list = ArrayList<String>()
            val v1 = "hello"
            val v2 = "hell"+"o"
            val v3 = v1
            (v1 === v2) shouldBe false
            (v1 === v3) shouldBe true
            list.add(v1)
            list.add(v2)
            list.add(v3)

        }
    }
}