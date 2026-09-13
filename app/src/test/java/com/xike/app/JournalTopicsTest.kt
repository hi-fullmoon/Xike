package com.xike.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTopicsTest {
    @Test
    fun `merged topics keep historical labels searchable without changing stored values`() {
        assertEquals(
            listOf("关系", "身体", "兴趣", "阅读"),
            listOf("社交", "运动", "创作", "阅读").canonicalTopics(),
        )
        assertEquals(listOf("关系", "社交"), expandedTopicLabels(listOf("关系")))
        assertEquals(listOf("身体", "运动"), expandedTopicLabels(listOf("运动")))
        assertEquals(listOf("兴趣", "创作"), expandedTopicLabels(listOf("兴趣")))
        assertEquals(listOf("阅读"), expandedTopicLabels(listOf("阅读")))
    }

    @Test
    fun `editing a merged topic removes either old or current label together`() {
        val oldAndCurrent = listOf("工作", "社交", "关系")

        assertTrue(oldAndCurrent.containsTopic("关系"))
        assertEquals(listOf("工作"), toggleTopic(oldAndCurrent, "关系"))
        assertEquals(listOf("工作", "关系"), toggleTopic(listOf("工作"), "关系"))
        assertFalse(listOf("工作").containsTopic("关系"))
    }
}
