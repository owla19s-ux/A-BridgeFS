package com.abridgefs.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun parse_list_command() {
        val result = CommandParser.parse("[list: .]")

        assertTrue(result.error.isNullOrBlank())
        assertEquals(1, result.commands.size)
        assertNotNull(result.commands.first())
    }

    @Test
    fun parse_commit_command() {
        val result = CommandParser.parse("[commit: README.md | 测试提交]")

        assertTrue(result.error.isNullOrBlank())
        assertEquals(1, result.commands.size)
        val command = result.commands.first()
        assertTrue(command.toString().contains("README.md"))
    }

    @Test
    fun reject_invalid_command() {
        val result = CommandParser.parse("[unknown: test]")

        assertTrue(result.error != null || result.commands.isEmpty())
    }
}
