package io.github.yingqiu0871.evolune.diagnostics

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeDiagnosticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tokenIdentityChangeStillRecordsSameLabel() {
        val surface = "D01-token-change"
        val token = mutableStateOf<Any>(Token(1))

        composeRule.setContent {
            RecordComposeRecomposition(
                surface = surface,
                state = "same-label",
                recompositionToken = token.value
            )
        }
        composeRule.waitForIdle()

        var before = 0L
        composeRule.runOnIdle { before = diagnosticCount(surface) }
        composeRule.runOnIdle { token.value = Token(2) }
        composeRule.waitForIdle()

        var after = 0L
        composeRule.runOnIdle { after = diagnosticCount(surface) }
        assertTrue("token change must be observed by the diagnostic", after > before)
    }

    @Test
    fun debugDiagnosticDoesNotInvokeTokenHashCode() {
        val surface = "D01-no-hash"
        val sentinel = ThrowingHashToken()

        composeRule.setContent {
            RecordComposeRecomposition(
                surface = surface,
                state = "sentinel",
                recompositionToken = sentinel
            )
        }
        composeRule.waitForIdle()

        var count = 0L
        composeRule.runOnIdle { count = diagnosticCount(surface) }
        assertTrue("debug diagnostic must record successfully", count > 0)
        assertEquals("token.hashCode() must not be called", 0, sentinel.hashCalls)
    }

    @Test
    fun nonDebuggablePathReturnsBeforeTokenInspection() {
        val surface = "D01-release-no-op"
        val sentinel = ThrowingHashToken()
        val nonDebuggableContext = NonDebuggableContext(
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides nonDebuggableContext) {
                RecordComposeRecomposition(
                    surface = surface,
                    state = "release",
                    recompositionToken = sentinel
                )
            }
        }
        composeRule.waitForIdle()

        var count = 0L
        composeRule.runOnIdle { count = diagnosticCount(surface) }
        assertEquals("release path must remain a no-op", 0, count)
        assertEquals("release path must inspect no token", 0, sentinel.hashCalls)
    }

    private fun diagnosticCount(surface: String): Long {
        val diagnosticsClass = Class.forName(
            "io.github.yingqiu0871.evolune.diagnostics.ComposeDiagnostics"
        )
        val instance = diagnosticsClass.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.get(null)
        @Suppress("UNCHECKED_CAST")
        val counts = diagnosticsClass.getDeclaredField("compositionCounts").apply {
            isAccessible = true
        }.get(instance) as Map<String, AtomicLong>
        return counts[surface]?.get() ?: 0L
    }

    private class Token(private val value: Int)

    private class ThrowingHashToken {
        var hashCalls: Int = 0

        override fun hashCode(): Int {
            hashCalls += 1
            throw AssertionError("sentinel hashCode must not be invoked")
        }
    }

    private class NonDebuggableContext(base: Context) : ContextWrapper(base) {
        private val info = ApplicationInfo(super.getApplicationInfo()).apply {
            flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        }

        override fun getApplicationInfo(): ApplicationInfo = info
    }
}
