package io.github.yingqiu0871.evolune.wear

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearLauncherIconResourceTest {
    @Test
    fun launcherIconResolvesToAdaptiveLayers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = context.getDrawable(R.mipmap.ic_launcher)

        assertTrue(
            "Wear launcher must resolve to an adaptive icon instead of a legacy bitmap wrapper",
            drawable is AdaptiveIconDrawable
        )
        val adaptiveIcon = drawable as AdaptiveIconDrawable
        assertNotNull("Adaptive Wear icon must provide a background layer", adaptiveIcon.background)
        assertNotNull("Adaptive Wear icon must provide a foreground layer", adaptiveIcon.foreground)
    }
}
