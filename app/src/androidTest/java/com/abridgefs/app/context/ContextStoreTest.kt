package com.abridgefs.app.context

import androidx.test.platform.app.InstrumentationRegistry
import com.abridgefs.app.github.GitHubResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ContextStoreTest {
    @Test
    fun context_persists_resources_and_can_be_deleted() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val firstStore = ContextStore(appContext)
        firstStore.delete("ctx-store-test")

        val repository = GitHubResource.Repository(
            id = 177,
            fullName = "owla19s-ux/APS",
            defaultBranch = "main"
        )
        val context = Context("ctx-store-test", "APS")
            .withResource(ResourceRef.GitHub(repository))

        firstStore.save(context)

        val secondStore = ContextStore(appContext)
        val restored = secondStore.get("ctx-store-test")

        assertNotNull(restored)
        assertEquals(context, restored)

        secondStore.delete("ctx-store-test")
        assertEquals(null, secondStore.get("ctx-store-test"))
    }

    @Test
    fun saving_same_id_replaces_existing_context() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ContextStore(appContext)
        store.delete("ctx-store-replace-test")

        store.save(Context("ctx-store-replace-test", "First"))
        store.save(Context("ctx-store-replace-test", "Second"))

        assertEquals(
            listOf(Context("ctx-store-replace-test", "Second")),
            store.all()
        )

        store.delete("ctx-store-replace-test")
    }
}
