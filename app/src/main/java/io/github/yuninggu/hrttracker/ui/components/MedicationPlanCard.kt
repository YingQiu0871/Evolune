package io.github.yuninggu.hrttracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yuninggu.hrttracker.data.MedicationPlan
import io.github.yuninggu.hrttracker.pk.Ester
import io.github.yuninggu.hrttracker.pk.Route
import io.github.yuninggu.hrttracker.ui.theme.HRTTrackerTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * 用药方案卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationPlanCard(
    plan: MedicationPlan,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (plan.isEnabled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题行：方案名称 + 启用开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (plan.isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Switch(
                    checked = plan.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    thumbContent = if (plan.isEnabled) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 方案描述
            Text(
                text = plan.getDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.isEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}

/**
 * 预览
 */
@Preview(showBackground = true)
@Composable
private fun MedicationPlanCardPreview() {
    HRTTrackerTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MedicationPlanCard(
                plan = MedicationPlan(
                    id = UUID.randomUUID(),
                    name = "EV注射",
                    route = Route.INJECTION,
                    ester = Ester.EV,
                    doseMG = 10.0,
                    scheduleType = MedicationPlan.ScheduleType.WEEKLY,
                    timeOfDay = listOf(LocalTime.of(9, 0)),
                    daysOfWeek = setOf(DayOfWeek.MONDAY),
                    isEnabled = true
                ),
                onClick = {},
                onToggleEnabled = {}
            )

            MedicationPlanCard(
                plan = MedicationPlan(
                    id = UUID.randomUUID(),
                    name = "E2凝胶",
                    route = Route.GEL,
                    ester = Ester.E2,
                    doseMG = 3.0,
                    scheduleType = MedicationPlan.ScheduleType.DAILY,
                    timeOfDay = listOf(LocalTime.of(23, 0)),
                    isEnabled = true
                ),
                onClick = {},
                onToggleEnabled = {}
            )

            MedicationPlanCard(
                plan = MedicationPlan(
                    id = UUID.randomUUID(),
                    name = "口服EV（已禁用）",
                    route = Route.ORAL,
                    ester = Ester.EV,
                    doseMG = 2.0,
                    scheduleType = MedicationPlan.ScheduleType.DAILY,
                    timeOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 30)),
                    isEnabled = false
                ),
                onClick = {},
                onToggleEnabled = {}
            )
        }
    }
}
