package com.xike.app

import com.xike.app.data.BackupValidationException
import com.xike.app.data.copyAttachmentTo
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupAttachmentLimitTest {
    @Test
    fun `exact individual and cumulative limits preserve all bytes`() {
        val content = "息刻附件".toByteArray()
        val total = longArrayOf(0)
        val output = ByteArrayOutputStream()
        repeat(2) {
            content.inputStream().use {
                it.copyAttachmentTo(
                    output,
                    content.size.toLong(),
                    total,
                    content.size * 2L,
                    "图片",
                    "备份图片",
                )
            }
        }
        assertArrayEquals(content + content, output.toByteArray())
        assertEquals(content.size * 2L, total[0])
        assertThrows(BackupValidationException::class.java) {
            content.inputStream().use {
                it.copyAttachmentTo(
                    output,
                    content.size.toLong(),
                    total,
                    content.size * 2L,
                    "图片",
                    "备份图片",
                )
            }
        }
        assertArrayEquals(content + content, output.toByteArray())
    }

    @Test
    fun `individual limit is enforced even without an archive total`() {
        val content = "息刻附件".toByteArray()
        val output = ByteArrayOutputStream()
        assertThrows(BackupValidationException::class.java) {
            content.inputStream().use {
                it.copyAttachmentTo(output, content.size - 1L, null, Long.MAX_VALUE, "录音", "备份录音")
            }
        }
        assertEquals(0, output.size())
    }
}
