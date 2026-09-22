package com.dynablox.launcher.core.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Categories drive the hub chips, the search index and the "hide a category" setting, so their ids
 * are persisted in SharedPreferences. Renaming one silently orphans a user preference — these tests
 * make that a build-time conversation instead of a support ticket.
 */
class CommandCategoryTest {

    @Test
    fun `category ids are unique and stable`() {
        val ids = CommandCategory.values().map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        // Persisted ids: hidden categories and ordering are stored by these strings.
        listOf("performance", "visual", "input", "projects", "apps", "system", "tools")
            .forEach { expected -> assertTrue("category $expected disappeared", ids.contains(expected)) }
    }

    @Test
    fun `every category has a label and an icon`() {
        CommandCategory.values().forEach { category ->
            assertTrue("${category.id} has no label", category.labelRes != 0)
            assertTrue("${category.id} has no icon", category.iconRes != 0)
        }
    }

    @Test
    fun `no two categories share a glyph`() {
        val icons = CommandCategory.values().map { it.iconRes }
        assertEquals(icons.size, icons.distinct().size)
    }

    @Test
    fun `tints stay inside the restrained palette`() {
        // The design direction forbids a rainbow: neutral plus four semantic accents, nothing else.
        assertEquals(
            setOf("NEUTRAL", "ACCENT", "SUCCESS", "WARNING", "DANGER"),
            CommandTint.values().map { it.name }.toSet(),
        )
    }

    @Test
    fun `requirements cover every privileged capability the hub exposes`() {
        val names = Requirement.values().map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("NONE", "OVERLAY", "SHIZUKU", "ACCESSIBILITY")))
    }

    @Test
    fun `feedback kinds cover the four outcomes a command can report`() {
        assertEquals(4, CommandFeedback.Kind.values().size)
    }
}
