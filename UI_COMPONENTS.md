# UI组件文档

## 用药记录列表项 (MedicationRecordItem)

### 组件概述

`MedicationRecordItem` 是一个Material 3 Expressive风格的卡片式列表项组件，用于显示单条用药记录。

### 组件结构

```
┌─────────────────────────────────────────────────────────┐
│  [图标]  │  药品名称           │  HH:mm            │
│          │  给药途径(暗色)     │  MM/dd            │
│          │  X.X mg             │                    │
└─────────────────────────────────────────────────────────┘
```

### 功能特性

- ✅ Material 3 设计规范
- ✅ 支持浅色/深色模式
- ✅ 自适应动态颜色（Android 12+）
- ✅ 给药途径图标映射
- ✅ 智能剂量格式化
- ✅ 时间日期显示
- ✅ 可选点击交互
- ✅ 完整的Preview支持

### 基本使用

#### 方式1：直接使用参数

```kotlin
MedicationRecordItem(
    medicationName = "雌二醇",
    route = Route.ORAL,
    doseMG = 2.0,
    timeH = currentTimeH,
    onClick = { /* 处理点击 */ }
)
```

#### 方式2：使用DoseEvent

```kotlin
val event = DoseEvent(
    route = Route.INJECTION,
    timeH = currentTimeH,
    doseMG = 5.0,
    ester = Ester.EV
)

MedicationRecordItem(
    event = event,
    onClick = { /* 处理点击 */ }
)
```

### 支持的给药途径及图标

| 给药途径 | 中文名称 | 图标 |
|---------|---------|------|
| `INJECTION` | 肌肉注射 | 💉 Vaccines |
| `ORAL` | 口服 | 💊 Medication |
| `SUBLINGUAL` | 舌下含服 | 💧 WaterDrop |
| `GEL` | 透皮凝胶 | 🧼 Soap |
| `PATCH_APPLY` | 应用贴片 | ➕ AddBox |
| `PATCH_REMOVE` | 移除贴片 | ☑️ IndeterminateCheckBox |

### 剂量显示规则

- **大于等于1mg**: 显示一位小数 (例: `2.0 mg`)
- **小于1mg**: 显示两位小数 (例: `0.25 mg`)

### 时间显示格式

- **时间**: `HH:mm` 格式 (例: `14:30`)
- **日期**: `MM/dd` 格式 (例: `02/28`)

### 组件参数

#### MedicationRecordItem (参数版本)

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| `medicationName` | String | ✅ | 药品名称 |
| `route` | Route | ✅ | 给药途径 |
| `doseMG` | Double | ✅ | 剂量（mg） |
| `timeH` | Double | ✅ | 时间（小时） |
| `modifier` | Modifier | ❌ | Compose修饰符 |
| `onClick` | (() -> Unit)? | ❌ | 点击回调 |

#### MedicationRecordItem (DoseEvent版本)

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| `event` | DoseEvent | ✅ | 用药事件对象 |
| `modifier` | Modifier | ❌ | Compose修饰符 |
| `onClick` | (() -> Unit)? | ❌ | 点击回调 |

### 内部辅助函数

#### getRouteIcon()
根据给药途径返回对应的Material Icons图标。

#### getRouteDisplayName()
返回给药途径的中文显示名称。

#### getMedicationDisplayName()
根据酯类返回药品的中文名称。

#### formatDose()
智能格式化剂量显示。

#### formatTime()
格式化时间为`HH:mm`格式。

#### formatDate()
格式化日期为`MM/dd`格式。

---

## 用药记录列表屏幕 (MedicationRecordsScreen)

### 组件概述

`MedicationRecordsScreen` 是一个完整的屏幕组件，包含顶部标题栏、用药记录列表和添加按钮。

### 组件结构

```
┌──────────────────────────────────┐
│  用药记录                    [标题栏]
├──────────────────────────────────┤
│                                  │
│  [用药记录项1]                   │
│  [用药记录项2]                   │
│  [用药记录项3]                   │
│  ...                             │
│                                  │
│                          [+]FAB  │
└──────────────────────────────────┘
```

### 功能特性

- ✅ Scaffold布局结构
- ✅ TopAppBar标题栏
- ✅ LazyColumn滚动列表
- ✅ FloatingActionButton添加按钮
- ✅ 空状态提示
- ✅ 支持深色模式

### 使用示例

```kotlin
@Composable
fun MyApp() {
    val events = remember { mutableStateListOf<DoseEvent>() }
    
    MedicationRecordsScreen(
        events = events,
        onEventClick = { event ->
            // 处理用药记录点击
            Log.d("MedicationRecords", "Clicked: ${event.id}")
        },
        onAddClick = {
            // 处理添加按钮点击
            // 导航到添加用药记录页面
        }
    )
}
```

### 组件参数

| 参数 | 类型 | 必需 | 说明 |
|-----|------|-----|------|
| `events` | List<DoseEvent> | ✅ | 用药事件列表 |
| `onEventClick` | (DoseEvent) -> Unit | ✅ | 点击记录回调 |
| `onAddClick` | () -> Unit | ✅ | 点击添加按钮回调 |
| `modifier` | Modifier | ❌ | Compose修饰符 |

### 状态处理

#### 空列表状态
当 `events` 为空时，显示友好的空状态提示：
- 居中显示添加图标
- "暂无用药记录"提示文字
- "点击右下角按钮添加用药记录"引导文字

#### 有数据状态
当 `events` 不为空时，显示滚动列表：
- 使用 `LazyColumn` 高效渲染
- 列表项之间有 8dp 间距
- 列表四周有 16dp 内边距

---

## Preview预览

### MedicationRecordItem预览

组件提供了多个预览变体：

1. **PreviewMedicationRecordItemOral** - 口服雌二醇
2. **PreviewMedicationRecordItemInjection** - 注射戊酸雌二醇
3. **PreviewMedicationRecordItemSublingual** - 舌下含服
4. **PreviewMedicationRecordItemGel** - 透皮凝胶
5. **PreviewMedicationRecordItemPatchApply** - 应用贴片
6. **PreviewMedicationRecordItemPatchRemove** - 移除贴片
7. **PreviewMedicationRecordItemSmallDose** - 小剂量显示
8. **PreviewMedicationRecordList** - 用药记录列表
9. **PreviewMedicationRecordItemFromEvent** - 使用DoseEvent

### MedicationRecordsScreen预览

1. **PreviewMedicationRecordsScreenEmpty** - 空列表
2. **PreviewMedicationRecordsScreenWithData** - 有数据列表
3. **PreviewMedicationRecordsScreenDark** - 深色模式

### 如何查看Preview

在Android Studio中：
1. 打开对应的Kotlin文件
2. 点击右上角的 **Split** 或 **Design** 视图
3. 所有的 `@Preview` 注解的Composable函数都会显示预览

---

## 样式定制

### 颜色方案

组件使用Material 3的动态颜色方案：

```kotlin
// 主色调
MaterialTheme.colorScheme.primary

// 容器颜色
MaterialTheme.colorScheme.primaryContainer

// 表面颜色
MaterialTheme.colorScheme.onSurface
MaterialTheme.colorScheme.onSurfaceVariant
```

### 排版

```kotlin
// 药品名称
MaterialTheme.typography.titleMedium

// 给药途径和剂量
MaterialTheme.typography.bodyMedium

// 日期
MaterialTheme.typography.bodySmall
```

### 自定义主题

如需自定义主题，修改 `Theme.kt` 中的颜色方案：

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = YourPrimaryColor,
    secondary = YourSecondaryColor,
    tertiary = YourTertiaryColor
)
```

---

## 与PK模块集成

### 数据流

```
DoseEvent (PK模块)
      ↓
MedicationRecordItem (UI组件)
      ↓
MedicationRecordsScreen (完整屏幕)
```

### 示例：创建完整的用药记录应用

```kotlin
@Composable
fun MedicationApp() {
    val events = remember {
        mutableStateListOf<DoseEvent>()
    }
    
    EvoluneTheme {
        MedicationRecordsScreen(
            events = events,
            onEventClick = { event ->
                // 显示详情或编辑
            },
            onAddClick = {
                // 添加新的用药记录
                events.add(
                    DoseEvent(
                        route = Route.ORAL,
                        timeH = System.currentTimeMillis() / 3600000.0,
                        doseMG = 2.0,
                        ester = Ester.E2
                    )
                )
            }
        )
    }
}
```

---

## 性能优化

### LazyColumn优化

- ✅ 使用 `key` 参数提供稳定的唯一标识
- ✅ 避免在 `items` 中进行复杂计算
- ✅ 使用 `remember` 缓存计算结果

### 示例

```kotlin
LazyColumn {
    items(
        items = events,
        key = { it.id }  // 使用UUID作为唯一key
    ) { event ->
        MedicationRecordItem(
            event = event,
            onClick = { onEventClick(event) }
        )
    }
}
```

---

## 可访问性

### 内容描述

所有图标都提供了适当的 `contentDescription`：

```kotlin
Icon(
    imageVector = getRouteIcon(route),
    contentDescription = getRouteDisplayName(route)
)
```

### 点击目标

- Card组件提供了足够大的点击区域
- 最小推荐点击尺寸：48dp × 48dp

---

## 未来扩展

### 可能的增强功能

1. **滑动操作**: 添加左滑/右滑删除或编辑
2. **长按菜单**: 长按显示更多操作选项
3. **筛选排序**: 按日期、途径、剂量排序
4. **搜索功能**: 快速查找特定记录
5. **分组显示**: 按日期分组显示
6. **统计信息**: 显示总剂量、频率等统计
7. **动画效果**: 添加列表项动画过渡

### 自定义扩展示例

```kotlin
@Composable
fun EnhancedMedicationRecordItem(
    event: DoseEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box {
        MedicationRecordItem(
            event = event,
            onClick = { showMenu = true },
            modifier = modifier
        )
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = {
                    showMenu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}
```

---

## 常见问题

### Q: 如何修改列表项的高度？
A: 通过修改 `padding` 值调整：
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),  // 调整此值
    // ...
)
```

### Q: 如何在列表中添加分隔线？
A: 在 `LazyColumn` 中添加 `Divider`：
```kotlin
items(events) { event ->
    MedicationRecordItem(event = event)
    Divider()
}
```

### Q: 如何处理时区问题？
A: 修改 `formatTime` 和 `formatDate` 函数使用特定时区：
```kotlin
private fun formatTime(timeH: Double, timeZone: TimeZone): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    sdf.timeZone = timeZone
    // ...
}
```

---

## 版本历史

- **v1.0** (2026-02-28)
  - 初始版本
  - 实现基础列表项组件
  - 实现完整屏幕组件
  - 添加所有Preview

---

## 许可证

本项目遵循开源许可证。详见 LICENSE 文件。
