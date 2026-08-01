# UI组件快速使用指南

## 🎯 核心组件

### 1. MedicationRecordItem - 用药记录列表项

**最简单的使用方式：**

```kotlin
MedicationRecordItem(
    event = DoseEvent(
        route = Route.ORAL,
        timeH = currentTimeH,
        doseMG = 2.0,
        ester = Ester.E2
    ),
    onClick = { /* 处理点击 */ }
)
```

**显示效果：**
```
┌─────────────────────────────────────────┐
│  💊  │  雌二醇                  14:30  │
│      │  口服                    02/28  │
│      │  2.0 mg                         │
└─────────────────────────────────────────┘
```

---

### 2. MedicationRecordsScreen - 完整屏幕

**基本用法：**

```kotlin
val events = remember { mutableStateListOf<DoseEvent>() }

MedicationRecordsScreen(
    events = events,
    onEventClick = { event -> /* 查看详情 */ },
    onAddClick = { /* 添加新记录 */ }
)
```

**包含功能：**
- ✅ 顶部标题栏
- ✅ 滚动列表
- ✅ 添加按钮（右下角FAB）
- ✅ 空状态提示

---

## 🎨 给药途径及其显示

| Route | 中文 | 图标 | 示例用法 |
|-------|-----|------|---------|
| `INJECTION` | 肌肉注射 | 💉 | 戊酸雌二醇注射 5mg |
| `ORAL` | 口服 | 💊 | 雌二醇口服 2mg |
| `SUBLINGUAL` | 舌下含服 | 💧 | 雌二醇舌下 1mg |
| `GEL` | 透皮凝胶 | 🧼 | 雌二醇凝胶 0.75mg |
| `PATCH_APPLY` | 应用贴片 | ➕ | 应用贴片 50µg/day |
| `PATCH_REMOVE` | 移除贴片 | ☑️ | 移除贴片 |

---

## 📝 常见场景示例

### 场景1: 添加新的用药记录

```kotlin
val events = remember { mutableStateListOf<DoseEvent>() }

Button(onClick = {
    events.add(
        DoseEvent(
            route = Route.ORAL,
            timeH = System.currentTimeMillis() / 3600000.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
    )
}) {
    Text("添加口服记录")
}
```

### 场景2: 显示记录详情

```kotlin
MedicationRecordsScreen(
    events = events,
    onEventClick = { event ->
        navController.navigate("detail/${event.id}")
    },
    onAddClick = {
        navController.navigate("add")
    }
)
```

### 场景3: 删除记录

```kotlin
val events = remember { mutableStateListOf<DoseEvent>() }

MedicationRecordsScreen(
    events = events,
    onEventClick = { event ->
        // 显示确认对话框
        showDialog = true
        selectedEvent = event
    },
    onAddClick = { /* ... */ }
)

if (showDialog) {
    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("删除记录") },
        text = { Text("确认删除这条用药记录吗？") },
        confirmButton = {
            TextButton(onClick = {
                events.remove(selectedEvent)
                showDialog = false
            }) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) {
                Text("取消")
            }
        }
    )
}
```

### 场景4: 不同酯类的记录

```kotlin
// 口服雌二醇
DoseEvent(route = Route.ORAL, doseMG = 2.0, ester = Ester.E2, timeH = now)

// 注射戊酸雌二醇
DoseEvent(route = Route.INJECTION, doseMG = 5.0, ester = Ester.EV, timeH = now)

// 注射环戊丙酸雌二醇
DoseEvent(route = Route.INJECTION, doseMG = 5.0, ester = Ester.EC, timeH = now)

// 注射庚酸雌二醇
DoseEvent(route = Route.INJECTION, doseMG = 10.0, ester = Ester.EN, timeH = now)
```

### 场景5: 舌下给药（设置档位）

```kotlin
// 使用快速档位（2分钟）
DoseEvent(
    route = Route.SUBLINGUAL,
    doseMG = 1.0,
    ester = Ester.E2,
    timeH = now,
    extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_TIER to 0.0)  // QUICK
)

// 使用标准档位（10分钟）
DoseEvent(
    route = Route.SUBLINGUAL,
    doseMG = 1.0,
    ester = Ester.E2,
    timeH = now,
    extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0)  // STANDARD
)
```

### 场景6: 贴片使用

```kotlin
// 应用贴片（50µg/天释放速率）
DoseEvent(
    route = Route.PATCH_APPLY,
    doseMG = 0.0,
    ester = Ester.E2,
    timeH = now,
    extras = mapOf(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
)

// 7天后移除贴片
DoseEvent(
    route = Route.PATCH_REMOVE,
    doseMG = 0.0,
    ester = Ester.E2,
    timeH = now + 168.0  // 7天 = 168小时
)
```

### 场景7: 凝胶应用

```kotlin
DoseEvent(
    route = Route.GEL,
    doseMG = 0.75,
    ester = Ester.E2,
    timeH = now,
    extras = mapOf(DoseEvent.ExtraKey.AREA_CM2 to 750.0)
)
```

---

## 🎭 预览所有变体

在Android Studio中打开以下文件可以看到所有预览：

1. **MedicationRecordItem.kt** - 单个列表项的各种预览
   - 口服雌二醇
   - 注射戊酸雌二醇
   - 舌下含服
   - 透皮凝胶
   - 应用/移除贴片
   - 小剂量显示
   - 完整列表

2. **MedicationRecordsScreen.kt** - 完整屏幕预览
   - 空列表状态
   - 有数据列表
   - 深色模式

---

## 🛠️ 自定义样式

### 修改列表项外观

```kotlin
MedicationRecordItem(
    event = event,
    modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .shadow(4.dp, RoundedCornerShape(12.dp)),
    onClick = { /* ... */ }
)
```

### 修改主题颜色

在 `Theme.kt` 中修改：

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE91E63),        // 粉色主色
    secondary = Color(0xFF673AB7),      // 紫色次要色
    tertiary = Color(0xFF009688),       // 青色点缀色
    // ... 其他颜色
)
```

---

## ⚡ 性能提示

### 使用key优化LazyColumn

```kotlin
LazyColumn {
    items(
        items = events,
        key = { it.id }  // ✅ 使用UUID作为key
    ) { event ->
        MedicationRecordItem(event = event)
    }
}
```

### 避免不必要的重组

```kotlin
// ❌ 不好的做法
onClick = { viewModel.onEventClick(event) }

// ✅ 好的做法
val onClick = remember(event.id) {
    { viewModel.onEventClick(event) }
}
onClick = onClick
```

---

## 🐛 常见问题

### Q: 时间显示不正确？
A: 确保 `timeH` 是从1970年1月1日起的小时数：
```kotlin
val currentTimeH = System.currentTimeMillis() / 3600000.0
```

### Q: 图标不显示？
A: 确保已添加Material Icons依赖。已包含在项目中。

### Q: 列表项点击无响应？
A: 检查是否传入了 `onClick` 参数：
```kotlin
MedicationRecordItem(
    event = event,
    onClick = { /* 必须提供回调 */ }
)
```

### Q: 如何排序列表？
```kotlin
val sortedEvents = remember(events) {
    events.sortedByDescending { it.timeH }  // 按时间倒序
}

MedicationRecordsScreen(
    events = sortedEvents,
    // ...
)
```

---

## 📱 完整示例：在MainActivity中使用

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EvoluneTheme {
                val events = remember {
                    mutableStateListOf<DoseEvent>()
                }

                MedicationRecordsScreen(
                    events = events,
                    onEventClick = { event ->
                        Toast.makeText(
                            this,
                            "查看记录: ${event.id}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onAddClick = {
                        // 添加新记录
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
    }
}
```

---

## 📚 相关文档

- [UI_COMPONENTS.md](UI_COMPONENTS.md) - 完整的UI组件文档
- [PK_IMPLEMENTATION.md](PK_IMPLEMENTATION.md) - 药代动力学模块文档

---

## ✅ 检查清单

创建新屏幕时需要：

- [ ] 导入 `EvoluneTheme`
- [ ] 使用 `remember` 管理状态
- [ ] 为所有交互提供回调函数
- [ ] 添加 `@Preview` 注解以便预览
- [ ] 处理空状态
- [ ] 使用 `Modifier` 进行布局调整
- [ ] 测试深色模式

---

**提示**: 在Android Studio中使用 **Live Edit** 功能可以实时查看UI变化！

**版本**: 1.0  
**更新日期**: 2026年2月28日
