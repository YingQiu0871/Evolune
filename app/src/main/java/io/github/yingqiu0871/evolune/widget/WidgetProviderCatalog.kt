package io.github.yingqiu0871.evolune.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * User-visible widget kinds are separate providers. The original receiver remains the
 * TODAY_PLAN provider so launcher bindings created by older releases keep their identity.
 */
internal object WidgetProviderCatalog {
    val providerClasses: List<Class<out EvoluneWidgetReceiver>> = listOf(
        EvoluneWidgetReceiver::class.java,
        NextDoseWidgetReceiver::class.java,
        CurrentE2WidgetReceiver::class.java,
        PkChartWidgetReceiver::class.java
    )

    fun styleFor(component: ComponentName?): WidgetStyle = when (component?.className) {
        NextDoseWidgetReceiver::class.java.name -> WidgetStyle.NEXT_DOSE
        CurrentE2WidgetReceiver::class.java.name -> WidgetStyle.CURRENT_E2
        PkChartWidgetReceiver::class.java.name -> WidgetStyle.PK_CHART
        else -> WidgetStyle.TODAY_PLAN
    }

    fun styleFor(context: Context, appWidgetId: Int): WidgetStyle = styleFor(
        AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
    )

    fun allWidgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return providerClasses
            .flatMap { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).asList()
            }
            .distinct()
            .toIntArray()
    }
}

class NextDoseWidgetReceiver : EvoluneWidgetReceiver()

class CurrentE2WidgetReceiver : EvoluneWidgetReceiver()

class PkChartWidgetReceiver : EvoluneWidgetReceiver()
