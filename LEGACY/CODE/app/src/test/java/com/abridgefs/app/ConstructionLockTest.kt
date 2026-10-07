package com.abridgefs.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionLockTest {

    @Test
    fun free_lock_has_no_holder() {
        val lock = ConstructionLock(
            projectId = "project",
            repository = "owner/repo",
            branch = "main"
        )

        assertTrue(lock.isFree)
        assertFalse(lock.heldBy("member"))
    }

    @Test
    fun held_lock_matches_only_its_holder() {
        val lock = ConstructionLock(
            projectId = "project",
            repository = "owner/repo",
            branch = "main",
            holderAiMemberId = "member-a",
            acquiredAt = 1L
        )

        assertFalse(lock.isFree)
        assertTrue(lock.heldBy("member-a"))
        assertFalse(lock.heldBy("member-b"))
        assertFalse(lock.heldBy(""))
    }
}
