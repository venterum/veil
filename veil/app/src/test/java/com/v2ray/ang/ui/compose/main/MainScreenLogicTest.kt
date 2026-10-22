package com.v2ray.ang.ui.compose.main

import com.v2ray.ang.dto.GroupMapItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenLogicTest {

    private val groups = listOf(
        GroupMapItem(id = "", remarks = "All", type = "all"),
        GroupMapItem(id = "work", remarks = "Work"),
        GroupMapItem(id = "private", remarks = "Private"),
    )

    @Test
    fun `finds the subscription group`() {
        assertEquals(1, findTargetGroupIndex(groups, "work"))
        assertEquals(2, findTargetGroupIndex(groups, "private"))
    }

    @Test
    fun `falls back to the all group`() {
        assertEquals(0, findTargetGroupIndex(groups, "removed-subscription"))
        assertEquals(0, findTargetGroupIndex(groups, ""))
    }

    @Test
    fun `returns -1 when there is no target`() {
        assertEquals(-1, findTargetGroupIndex(emptyList(), "work"))
        assertEquals(
            -1,
            findTargetGroupIndex(listOf(GroupMapItem(id = "work", remarks = "Work")), "other"),
        )
    }
}
