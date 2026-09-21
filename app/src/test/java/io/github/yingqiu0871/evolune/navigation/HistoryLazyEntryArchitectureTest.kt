package io.github.yingqiu0871.evolune.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * B-02/F06 JVM architecture proof for lazy History entry wiring.
 *
 * The root is intentionally checked as source architecture: the actual Compose destination is
 * Android-owned, while the acceptance property is that the root passes a stable factory and only
 * the History destination requests the Activity-owned ViewModel.
 */
class HistoryLazyEntryArchitectureTest {

    private val mainActivity = source(
        "src/main/java/io/github/yingqiu0871/evolune/MainActivity.kt"
    )
    private val appNavigation = source(
        "src/main/java/io/github/yingqiu0871/evolune/navigation/AppNavigation.kt"
    )

    @Test
    fun `cold root wiring does not request HistoryViewModel`() {
        assertFalse(
            "MainActivity must not instantiate HistoryViewModel before navigation",
            mainActivity.contains("val historyViewModel: HistoryViewModel = viewModel")
        )
        assertTrue(
            "the root must pass the stable Activity-owned factory",
            mainActivity.contains(
                "historyViewModelFactory = mainFeatureServices.historyViewModelFactory"
            )
        )
    }

    @Test
    fun `History entry creates exactly one Activity-owned ViewModel`() {
        val destination = historyDestination()

        assertEquals(1, destination.count("val historyViewModel: HistoryViewModel = viewModel"))
        assertTrue(destination.contains("viewModelStoreOwner = owner"))
        assertTrue(destination.contains("factory = historyViewModelFactory"))
        assertTrue(destination.contains("HistoryScreen(\n                    viewModel = historyViewModel"))
    }

    @Test
    fun `root recomposition carries the provider without invoking it`() {
        assertEquals(
            1,
            mainActivity.count("historyViewModelFactory = mainFeatureServices.historyViewModelFactory")
        )
        assertFalse(
            mainActivity.contains("viewModel(\n                    factory = mainFeatureServices.historyViewModelFactory")
        )
    }

    @Test
    fun `History re-entry preserves the existing Activity ViewModel owner`() {
        val destination = historyDestination()

        assertTrue(
            destination.contains(
                "val owner: ViewModelStoreOwner = activity as? ViewModelStoreOwner ?: entry"
            )
        )
        assertTrue(
            "History must use the same Activity owner discipline as the other feature surfaces",
            destination.contains("viewModelStoreOwner = owner")
        )
    }

    private fun historyDestination(): String = appNavigation
        .substringAfter("composable(Screen.HISTORY.route) { entry ->")
        .substringBefore("composable(INSIGHTS_ROUTE)")

    private fun source(relativePath: String): String = Files.readString(Path.of(relativePath))

    private fun String.count(needle: String): Int = windowed(needle.length, 1).count { it == needle }
}
