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
        assertEquals(
            listOf(Command.Commit("README.md", "测试提交")),
            result.commands
        )
    }

    @Test
    fun parse_edit_command() {
        val result = CommandParser.parse("[edit: README.md]旧内容====新内容[/edit]")

        assertTrue(result.error.isNullOrBlank())
        assertEquals(
            listOf(Command.Edit("README.md", "旧内容", "新内容")),
            result.commands
        )
    }

    @Test
    fun reject_unknown_command() {
        val result = CommandParser.parse("[unknown: test]")

        assertTrue(result.commands.isEmpty())
        assertTrue(result.error != null)
    }

    @Test
    fun reject_unclosed_write_command() {
        val result = CommandParser.parse("[write: README.md]内容")

        assertTrue(result.commands.isEmpty())
        assertEquals("write 指令缺少 [/write] 结束标记，未写入文件", result.error)
    }
}
