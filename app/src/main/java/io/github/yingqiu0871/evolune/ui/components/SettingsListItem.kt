package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text

/** Shared settings list treatment used by the settings home and sub-pages. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun settingsListItemColors(): ListItemColors = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    trailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    supportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun stableSegmentedShapes(index: Int, count: Int): ListItemShapes {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    return shapes.copy(
        selectedShape = shapes.shape,
        pressedShape = shapes.shape,
        focusedShape = shapes.shape,
        hoveredShape = shapes.shape,
        draggedShape = shapes.shape
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsNavigationRow(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SegmentedListItem(
        modifier = modifier,
        onClick = onClick,
        shapes = stableSegmentedShapes(index = 0, count = 1),
        colors = settingsListItemColors(),
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        supportingContent = { Text(description) },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null
            )
        }
    ) {
        Text(title)
    }
}
