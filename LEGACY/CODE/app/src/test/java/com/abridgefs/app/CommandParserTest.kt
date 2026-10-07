package com.abridgefs.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun parse_list_command() {
        val result = CommandParser.parse("[list]")

        assertTrue(result.error.isNullOrBlank())
        assertEquals(listOf(Command.ListTree), result.commands)
    }

    @Test
    fun parse_commit_command() {
        val result = CommandParser.parse("[commit: README.md | 测试提交]")

        assertTrue(result.error.isNullOrBlank())
        assertEquals(listOf(Command.Commit("README.md", "测试提交")), result.commands)
    }

    @Test
    fun reject_invalid_command() {
        val result = CommandParser.parse("[unknown: test]")

        assertTrue(result.error != null)
        assertTrue(result.commands.isEmpty())
    }
}
