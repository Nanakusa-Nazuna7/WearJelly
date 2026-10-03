package dev.wearjelly.ui

/**
 * 全局统一文案（REQ-COPY-MT-004）：菜单、操作栏与提示逐字以此为准。
 * 规格要点：单曲菜单禁止出现"全部缓存/全部入队"类批量项；缓存类文案用"缓存"不用"下载"。
 */
internal object Copy {
    const val PLAY = "播放"
    const val PLAY_NEXT = "下一首播放"
    const val ADD_TO_QUEUE = "加入待播队列"
    const val PLAY_ALL_FROM_HERE = "以此开始播放全部"
    const val CACHE_THIS = "缓存这首"
    const val DELETE_THIS_CACHE = "删除这首的缓存"
    const val SONG_INFO = "查看信息"
    const val SHARE = "分享"
    const val REMOVE_FROM_LIST = "从当前列表移除"

    const val PLAY_ALL = "播放全部"
    const val SHUFFLE_PLAY_ALL = "随机播放全部"
    const val QUEUE_ALL = "全部加入待播队列"
    const val CACHE_ALL = "缓存当前列表全部"
    const val DELETE_ALL_CACHES = "删除当前列表全部缓存"

    const val SELECT_ALL = "全选"
    const val SELECT_NONE = "取消全选"
    const val INVERT_SELECTION = "反选"
    const val EXIT_SELECTION = "退出多选"
    const val MORE = "更多"

    fun selectedCount(count: Int) = "已选 $count 首"
    fun cacheSelected(count: Int) = "缓存选中项（$count）"
    const val DELETE_SELECTED_CACHES = "删除选中项缓存"
}
