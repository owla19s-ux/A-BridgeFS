package com.abridgefs.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionPolicyTest {

    @Test
    fun read_is_allowed_when_action_is_allowed() {
        val authorization = Authorization(
            root = "/tmp/project",
            allowed = setOf(FileAction.LIST, FileAction.READ),
            confirm = emptySet()
        )

        assertEquals(Decision.ALLOW, authorization.decide(FileAction.READ))
    }

    @Test
    fun write_requires_confirmation_when_configured() {
        val authorization = Authorization(
            root = "/tmp/project",
            allowed = setOf(FileAction.LIST, FileAction.READ, FileAction.WRITE),
            confirm = setOf(FileAction.WRITE)
        )

        assertEquals(Decision.CONFIRM, authorization.decide(FileAction.WRITE))
    }

    @Test
    fun commit_is_denied_when_action_is_not_allowed() {
        val authorization = Authorization(
            root = "/tmp/project",
            allowed = setOf(FileAction.LIST, FileAction.READ),
            confirm = emptySet()
        )

        assertEquals(Decision.DENY, authorization.decide(FileAction.COMMIT))
    }

    @Test
    fun confirm_takes_priority_over_allow() {
        val authorization = Authorization(
            root = "/tmp/project",
            allowed = setOf(FileAction.READ),
            confirm = setOf(FileAction.READ)
        )

        assertEquals(Decision.CONFIRM, authorization.decide(FileAction.READ))
    }
}
