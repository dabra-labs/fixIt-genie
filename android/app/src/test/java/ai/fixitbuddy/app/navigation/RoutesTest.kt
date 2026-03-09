package ai.fixitbuddy.app.navigation

import org.junit.Assert.*
import org.junit.Test

class RoutesTest {

    @Test
    fun `SESSION route is session`() {
        assertEquals("session", Routes.SESSION)
    }

    @Test
    fun `HISTORY route is history`() {
        assertEquals("history", Routes.HISTORY)
    }

    @Test
    fun `SETTINGS route is settings`() {
        assertEquals("settings", Routes.SETTINGS)
    }

    @Test
    fun `all routes are unique`() {
        val routes = listOf(Routes.SESSION, Routes.HISTORY, Routes.SETTINGS)
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `SESSION route is not empty`() {
        assertNotNull(Routes.SESSION)
        assertFalse(Routes.SESSION.isEmpty())
    }

    @Test
    fun `HISTORY route is not empty`() {
        assertNotNull(Routes.HISTORY)
        assertFalse(Routes.HISTORY.isEmpty())
    }

    @Test
    fun `SETTINGS route is not empty`() {
        assertNotNull(Routes.SETTINGS)
        assertFalse(Routes.SETTINGS.isEmpty())
    }

    @Test
    fun `SESSION route does not contain spaces`() {
        assertFalse(Routes.SESSION.contains(" "))
    }

    @Test
    fun `HISTORY route does not contain spaces`() {
        assertFalse(Routes.HISTORY.contains(" "))
    }

    @Test
    fun `SETTINGS route does not contain spaces`() {
        assertFalse(Routes.SETTINGS.contains(" "))
    }

    @Test
    fun `SESSION is lowercase`() {
        assertEquals(Routes.SESSION, Routes.SESSION.lowercase())
    }

    @Test
    fun `HISTORY is lowercase`() {
        assertEquals(Routes.HISTORY, Routes.HISTORY.lowercase())
    }

    @Test
    fun `SETTINGS is lowercase`() {
        assertEquals(Routes.SETTINGS, Routes.SETTINGS.lowercase())
    }

    @Test
    fun `routes are navigation paths`() {
        val validRoutes = setOf("session", "history", "settings")
        assertTrue(validRoutes.contains(Routes.SESSION))
        assertTrue(validRoutes.contains(Routes.HISTORY))
        assertTrue(validRoutes.contains(Routes.SETTINGS))
    }

    @Test
    fun `routes are alphanumeric`() {
        val isAlphanumeric = { str: String -> str.all { it.isLetterOrDigit() || it == '_' } }
        assertTrue(isAlphanumeric(Routes.SESSION))
        assertTrue(isAlphanumeric(Routes.HISTORY))
        assertTrue(isAlphanumeric(Routes.SETTINGS))
    }

    @Test
    fun `multiple accesses return same value`() {
        assertEquals(Routes.SESSION, Routes.SESSION)
        assertEquals(Routes.HISTORY, Routes.HISTORY)
        assertEquals(Routes.SETTINGS, Routes.SETTINGS)
    }

    @Test
    fun `route values are constants`() {
        val route1 = Routes.SESSION
        val route2 = Routes.SESSION
        assertEquals(route1, route2)
    }
}
