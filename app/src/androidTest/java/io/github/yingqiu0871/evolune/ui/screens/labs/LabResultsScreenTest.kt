package io.github.yingqiu0871.evolune.ui.screens.labs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.labs.LabResultsViewModel
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LabResultsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = InMemoryLabRepository()

    @Test
    fun addEditAndDeleteALabResult() {
        launch()
        composeRule.onNodeWithTag("lab-results-empty").assertIsDisplayed()

        composeRule.onNodeWithTag("lab-results-add").performClick()
        composeRule.onNodeWithTag("lab-result-value").performTextInput("180.5")
        composeRule.onNodeWithTag("lab-result-save").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { repository.results.value.size == 1 }
        composeRule.onNodeWithText("180.5 pg/mL").assertIsDisplayed()

        val id = repository.results.value.single().id
        composeRule.onNodeWithTag("lab-result-row-$id").performClick()
        composeRule.onNodeWithTag("lab-result-value").performTextClearance()
        composeRule.onNodeWithTag("lab-result-value").performTextInput("662")
        composeRule.onNodeWithTag("lab-result-unit-${LabUnit.PMOL_PER_L.code}").performClick()
        composeRule.onNodeWithTag("lab-result-save").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { repository.results.value.single().revision == 2L }
        composeRule.onNodeWithText("662 pmol/L").assertIsDisplayed()
        assertEquals(LabUnit.PMOL_PER_L, repository.results.value.single().unit)

        composeRule.onNodeWithTag("lab-result-row-$id").performClick()
        composeRule.onNodeWithTag("lab-result-delete").performClick()
        composeRule.onNodeWithTag("lab-result-delete-confirm-button").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { repository.results.value.isEmpty() }
        composeRule.onNodeWithTag("lab-results-empty").assertIsDisplayed()
    }

    @Test
    fun invalidValueKeepsTheEditorOpenWithAnError() {
        launch()

        composeRule.onNodeWithTag("lab-results-add").performClick()
        composeRule.onNodeWithTag("lab-result-value").performTextInput("0")
        composeRule.onNodeWithTag("lab-result-save").performClick()

        composeRule.onNodeWithTag("lab-result-error").assertIsDisplayed()
        composeRule.onNodeWithTag("lab-result-editor").assertIsDisplayed()
        assertEquals(emptyList<LabResult>(), repository.results.value)
    }

    private fun launch() {
        val viewModel = LabResultsViewModel(repository)
        composeRule.setContent {
            EvoluneTheme {
                LabResultsRoute(viewModel = viewModel, is24Hour = true)
            }
        }
        composeRule.waitForIdle()
    }

    private class InMemoryLabRepository : LabResultRepository {
        val results = MutableStateFlow<List<LabResult>>(emptyList())

        override fun observeAll(): Flow<List<LabResult>> = results

        override suspend fun getById(id: UUID): LabResult? = results.value.firstOrNull { it.id == id }

        override suspend fun insert(result: LabResult): InsertResult {
            results.update { it + result }
            return InsertResult.Inserted
        }

        override suspend fun update(result: LabResult, expectedRevision: Long): UpdateResult {
            val current = getById(result.id) ?: return UpdateResult.NotFound
            if (current.revision != expectedRevision) return UpdateResult.RevisionConflict
            val next = result.copy(revision = expectedRevision + 1)
            results.update { list -> list.map { if (it.id == result.id) next else it } }
            return UpdateResult.Updated
        }

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult {
            val current = getById(id) ?: return ConditionalDeleteResult.NotFound
            if (current.revision != expectedRevision) return ConditionalDeleteResult.RevisionConflict
            results.update { list -> list.filterNot { it.id == id } }
            return ConditionalDeleteResult.Deleted
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
