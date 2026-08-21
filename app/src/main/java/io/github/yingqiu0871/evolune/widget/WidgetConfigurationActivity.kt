package io.github.yingqiu0871.evolune.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = configuredAppWidgetId(intent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val controller = WidgetConfigurationController(
            repository = WidgetAppearanceStore(this),
            refresh = { id ->
                requestEvoluneWidgetUpdate(
                    applicationContext,
                    WidgetUpdateReason.APPEARANCE_CHANGED,
                    intArrayOf(id)
                )
            }
        )
        setResult(RESULT_OK, resultIntent)
        setContent {
            EvoluneTheme {
                var config by remember { mutableStateOf(controller.load(appWidgetId)) }
                WidgetConfigurationScreen(
                    config = config,
                    onConfigChange = { config = it.normalized() },
                    onRestoreDefaults = { config = controller.restoreDefaults() },
                    onCancel = {
                        controller.cancel()
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onApply = {
                        lifecycleScope.launch {
                            controller.apply(appWidgetId, config)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

internal fun configuredAppWidgetId(intent: Intent?): Int = intent?.getIntExtra(
    AppWidgetManager.EXTRA_APPWIDGET_ID,
    AppWidgetManager.INVALID_APPWIDGET_ID
) ?: AppWidgetManager.INVALID_APPWIDGET_ID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigurationScreen(
    config: WidgetAppearanceConfig,
    onConfigChange: (WidgetAppearanceConfig) -> Unit,
    onRestoreDefaults: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.widget_config_apply))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            WidgetConfigurationPreview(config)
            ConfigurationSectionTitle(R.string.widget_config_theme)
            ThemeSelector(config.themeMode) { mode ->
                onConfigChange(config.copy(themeMode = mode))
            }
            HorizontalDivider()
            ConfigurationSectionTitle(R.string.widget_config_color_scheme)
            PaletteSelector(config.colorScheme) { scheme ->
                onConfigChange(config.copy(colorScheme = scheme))
            }
            HorizontalDivider()
            OpacitySelector(config.backgroundOpacity) { opacity ->
                onConfigChange(config.copy(backgroundOpacity = opacity))
            }
            OutlinedButton(onClick = onRestoreDefaults) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.widget_config_restore_defaults))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfigurationSectionTitle(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ThemeSelector(
    selected: WidgetThemeMode,
    onSelected: (WidgetThemeMode) -> Unit
) {
    val options = WidgetThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = {
                    Text(
                        stringResource(mode.labelRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun PaletteSelector(
    selected: WidgetColorScheme,
    onSelected: (WidgetColorScheme) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WidgetColorScheme.entries.forEach { scheme ->
            val palette = WidgetPaletteResolver.resolve(
                config = WidgetAppearanceConfig(colorScheme = scheme),
                dark = false
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(scheme) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == scheme) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    if (selected == scheme) 2.dp else 1.dp,
                    if (selected == scheme) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(scheme.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            palette.primary,
                            palette.secondary,
                            palette.primaryContainer,
                            palette.surface
                        ).forEach { color ->
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { onSelected(scheme) }
                            ) {
                                Surface(color = Color(color), modifier = Modifier.fillMaxSize()) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpacitySelector(opacity: Float, onOpacityChange: (Float) -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.widget_config_background_opacity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.widget_config_opacity_value, (value * 100f).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = {
                value = it
                onOpacityChange(it)
            },
            valueRange = MIN_WIDGET_BACKGROUND_OPACITY..1f
        )
        Text(
            stringResource(R.string.widget_config_opacity_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WidgetConfigurationPreview(config: WidgetAppearanceConfig) {
    val context = LocalContext.current
    val palette = WidgetPaletteResolver.resolve(context, config)
    val previewBackground = Color(palette.surface)
        .copy(alpha = config.backgroundOpacity)
        .compositeOver(MaterialTheme.colorScheme.background)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val previewShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(248.dp)
                .shadow(3.dp, previewShape)
                .clip(previewShape)
                .background(previewBackground, previewShape)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.widget_preview_summary),
                        color = Color(palette.onSurface),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.widget_preview_concentration),
                        color = Color(palette.primaryForeground),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(palette.primary, palette.progressTrack, palette.progressTrack).forEach {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp),
                            shape = RoundedCornerShape(3.dp),
                            color = Color(it)
                        ) {}
                    }
                }
                PreviewMedicationRow(
                    stringResource(R.string.widget_preview_plan_one),
                    stringResource(R.string.widget_preview_meta_one),
                    stringResource(R.string.widget_completed),
                    palette,
                    0,
                    completed = true
                )
                PreviewMedicationRow(
                    stringResource(R.string.widget_preview_plan_two),
                    stringResource(R.string.widget_preview_meta_two),
                    "09:00",
                    palette,
                    1,
                    completed = false
                )
                PreviewMedicationRow(
                    stringResource(R.string.widget_preview_plan_three),
                    stringResource(R.string.widget_preview_meta_three),
                    "21:00",
                    palette,
                    2,
                    completed = false
                )
            }
        }
    }
}

@Composable
private fun PreviewMedicationRow(
    title: String,
    meta: String,
    status: String,
    palette: WidgetPalette,
    railRoleIndex: Int,
    completed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp),
            shape = RoundedCornerShape(2.dp),
            color = Color(palette.medicationRailColor(railRoleIndex))
        ) {}
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                title,
                color = Color(palette.onSurface),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(meta, color = Color(palette.onSurfaceVariant), fontSize = 10.sp, maxLines = 1)
        }
        Text(
            status,
            color = Color(palette.primaryForeground),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(4.dp))
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = if (completed) Color(palette.primaryContainer) else Color.Transparent,
            border = if (completed) null else BorderStroke(1.dp, Color(palette.primaryForeground))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (completed) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.Check
                    },
                    contentDescription = null,
                    tint = if (completed) {
                        Color(palette.onPrimaryContainer)
                    } else {
                        Color(palette.primaryForeground)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun WidgetThemeMode.labelRes() = when (this) {
    WidgetThemeMode.AUTO -> R.string.widget_config_theme_auto
    WidgetThemeMode.LIGHT -> R.string.widget_config_theme_light
    WidgetThemeMode.DARK -> R.string.widget_config_theme_dark
}

private fun WidgetColorScheme.labelRes() = when (this) {
    WidgetColorScheme.MATERIAL_YOU_AUTO -> R.string.widget_config_material_you
    WidgetColorScheme.MONET_BLUE -> R.string.widget_config_palette_blue
    WidgetColorScheme.MONET_VIOLET -> R.string.widget_config_palette_violet
    WidgetColorScheme.MONET_SAKURA -> R.string.widget_config_palette_sakura
    WidgetColorScheme.MONET_MINT -> R.string.widget_config_palette_mint
    WidgetColorScheme.MONET_TEAL -> R.string.widget_config_palette_teal
    WidgetColorScheme.MONET_AMBER -> R.string.widget_config_palette_amber
    WidgetColorScheme.MONET_NEUTRAL -> R.string.widget_config_palette_neutral
    WidgetColorScheme.MONET_LAVENDER -> R.string.widget_config_palette_lavender
}
