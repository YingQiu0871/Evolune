package io.github.yingqiu0871.evolune.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class MotionListUxBoundaryTest {
    @Test
    fun `record list keeps newest-first stable identity with item animation`() {
        val source = source("ui/screens/MedicationRecordsScreen.kt")

        assertTrue(source.contains("events.sortedByDescending { it.occurredAt }"))
        assertTrue(source.contains("key = { it.id }"))
        assertTrue(source.contains("modifier = Modifier.animateItem()"))
    }

    @Test
    fun `record and plan overlays share session-driven transition host`() {
        val source = source("navigation/AppNavigation.kt")
        val hostSource = source("ui/components/EditorTransitionHost.kt")
        val motionSource = source("ui/motion/EvolunePageMotion.kt")

        assertTrue(source.contains("session = recordEditSession"))
        assertTrue(source.contains("session = planEditSession"))
        assertTrue(source.split("EditorTransitionHost(").size - 1 >= 2)
        assertFalse(source.contains("if (recordEditSession != null)"))
        assertFalse(source.contains("if (planEditSession != null)"))
        assertTrue(hostSource.contains("evolunePageEnterTransition(startImmediately = true)"))
        assertTrue(hostSource.contains("evolunePageExitTransition()"))
        assertTrue(hostSource.contains("sizeTransform = null"))
        assertFalse(hostSource.contains("slideIn"))
        assertFalse(hostSource.contains("slideOut"))
        assertFalse(hostSource.contains("BottomEnd"))
        assertFalse(hostSource.contains("scaleOut"))
        assertTrue(source.split("evolunePageEnterTransition()").size - 1 >= 2)
        assertTrue(source.split("evolunePageExitTransition()").size - 1 >= 2)
        assertTrue(motionSource.contains("initialScale = PAGE_INITIAL_SCALE"))
        assertTrue(motionSource.contains("TransformOrigin.Center"))
        assertTrue(motionSource.contains("PAGE_INITIAL_SCALE = 0.98f"))
        assertTrue(motionSource.contains("NO_PAGE_ENTER_DELAY_MILLIS = 0"))
        assertFalse(motionSource.contains("slideIn"))
        assertFalse(motionSource.contains("slideOut"))
        assertFalse(motionSource.contains("BottomEnd"))
    }

    @Test
    fun `editor layers cover a persistent top-level scaffold`() {
        val source = source("navigation/AppNavigation.kt")

        assertTrue(source.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(source.contains("The top-level Scaffold stays intact"))
        assertTrue(source.contains("BottomNavigationBar(navController = navController)"))
        assertTrue(source.contains("NavigationRailBar(navController = navController)"))
        assertTrue(source.split("modifier = Modifier.fillMaxSize()").size - 1 >= 2)
        assertFalse(source.contains("navigationChromeState"))
        assertFalse(source.contains("MutableTransitionState"))
        assertFalse(source.contains("AnimatedVisibility"))
        assertFalse(source.contains("delay("))
    }

    private fun source(relativePath: String): String = Files.readString(
        Path.of("src/main/java/io/github/yingqiu0871/evolune/$relativePath")
    )
}
