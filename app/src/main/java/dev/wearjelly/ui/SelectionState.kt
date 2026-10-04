package dev.wearjelly.ui

/**
 * 歌曲多选状态（MT 管理器式）：NORMAL ⇄ MULTI(active, selectedIds, anchorId)。
 * 纯 Kotlin 逻辑，无 Android 依赖，便于 JVM 单测。
 *
 * 不变式：除显式进入入口外，active=true 时 selectedIds 不允许为空；
 * 任何 1→0 的转换必须落回 NORMAL（active=false）。
 */
internal data class SelectionSnapshot(
    val active: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /** 锚点：最近一次左滑进入多选选中项 / 单击后变为选中的项 / 范围选择的终点项。 */
    val anchorId: String? = null,
)

internal object SelectionLogic {

    /** 普通模式左滑条目：选中该条目并进入多选，锚点指向它。 */
    fun enterFromSwipe(itemId: String): SelectionSnapshot =
        SelectionSnapshot(active = true, selectedIds = setOf(itemId), anchorId = itemId)

    /**
     * 多选模式单击条目：单独切换选中态。
     * 取消选中不动锚点；归零时自动退出多选。
     */
    fun toggled(current: SelectionSnapshot, itemId: String): SelectionSnapshot {
        if (!current.active) return current
        val nowSelected = itemId !in current.selectedIds
        val next = if (nowSelected) current.selectedIds + itemId else current.selectedIds - itemId
        return if (next.isEmpty()) {
            cleared()
        } else {
            current.copy(selectedIds = next, anchorId = if (nowSelected) itemId else current.anchorId)
        }
    }

    /**
     * 多选模式左滑条目：范围选择 `target..anchor`（按 orderedIds 的列表序），
     * 锚点更新为本次左滑的终点 target。锚点为空或已不在列表中时退化为单选 target。
     */
    fun rangeSelected(
        current: SelectionSnapshot,
        targetId: String,
        orderedIds: List<String>,
    ): SelectionSnapshot {
        if (!current.active) return current
        val targetIdx = orderedIds.indexOf(targetId)
        if (targetIdx < 0) return current
        val anchorIdx = current.anchorId?.let(orderedIds::indexOf) ?: -1
        if (anchorIdx < 0) return enterFromSwipe(targetId)
        val next = current.selectedIds +
            orderedIds.subList(minOf(anchorIdx, targetIdx), maxOf(anchorIdx, targetIdx) + 1).toSet()
        return current.copy(selectedIds = next, anchorId = targetId)
    }

    /** 全选（orderedIds 为当前列表完整有序 id）。 */
    fun allSelected(current: SelectionSnapshot, orderedIds: List<String>): SelectionSnapshot =
        current.copy(selectedIds = current.selectedIds + orderedIds.toSet())

    /**
     * 反选：选中状态在 orderedIds 范围内取反。归零时自动退出多选。
     */
    fun inverted(current: SelectionSnapshot, orderedIds: List<String>): SelectionSnapshot {
        if (!current.active) return current
        val next = orderedIds.toSet() - current.selectedIds
        return if (next.isEmpty()) cleared() else current.copy(selectedIds = next)
    }

    fun cleared(): SelectionSnapshot = SelectionSnapshot()
}
