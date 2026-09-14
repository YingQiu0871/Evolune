package io.github.yingqiu0871.evolune.history.insights

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * v1.7-B-03 regression: `InsightsRangeSelection.DEFAULT` must be readable in **every** class
 * initialization order.
 *
 * The interface declares a default method, so the JVM initializes it before any implementor that is
 * touched first. The companion object used to fill `DEFAULT` from `Last30Days.INSTANCE` in its own
 * static initializer: in the object-first order that read happens while `Last30Days` is still being
 * initialized, returns `null`, and leaves `DEFAULT` permanently null — a process-wide crash
 * (`InsightsUiState(selection = null)`).
 *
 * The test loads the production classes in a **fresh classloader** so it can force that order, which
 * cannot be reproduced inside an already-warm JVM.
 */
class InsightsRangeSelectionInitTest {

    private fun freshLoader(): ClassLoader {
        val urls = System.getProperty("java.class.path").orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it).toURI().toURL() }
            .toTypedArray()
        // parent = null keeps the production classes out of the already-warm app loader
        return URLClassLoader(urls, null)
    }

    @Test
    fun `the default selection survives an object-first initialization order`() {
        val loader = freshLoader()

        // 1. touch the data object first: the JVM must initialize the super interface (it declares
        //    a default method) before the object's own static initializer runs
        val last30 = Class.forName(
            "io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection\$Last30Days",
            true,
            loader
        )
        val instance = last30.getField("INSTANCE").get(null)
        assertNotNull("the data object itself must initialize", instance)

        // 2. the frozen default must still resolve to that same instance
        val selection = Class.forName(
            "io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection",
            true,
            loader
        )
        val companion = selection.getField("Companion").get(null)
        val default = companion.javaClass.getMethod("getDEFAULT").invoke(companion)

        assertNotNull("InsightsRangeSelection.DEFAULT must never be null", default)
        assertSame(instance, default)
    }
}
