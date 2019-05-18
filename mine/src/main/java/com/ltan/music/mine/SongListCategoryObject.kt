package com.ltan.music.mine

import com.ltan.music.widget.constants.State

/**
 * TMusic.com.ltan.music.mine
 *
 * @ClassName: SongListCategoryObject
 * @Description:
 * @Author: tanlin
 * @Date:   2019-05-03
 * @Version: 1.0
 */
class SongListCategoryObject constructor(val id: Int, val title: String = "", val count: Int = 0, var creatable: Boolean = false) {
    var state = State.EXPAND
}