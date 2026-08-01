# 用药方案功能实现文档

## 功能概述

本次更新实现了完整的用药方案功能，包括：

1. **用药方案设置**：用户可以创建和管理用药方案
2. **用药提醒**：基于用药方案的定时提醒功能
3. **未来血药浓度预测**：根据用药方案计算未来15天的血药浓度曲线

## 实现的功能

### 1. 用药方案管理

#### 数据模型 (`MedicationPlan`)

- **基本属性**：
  - 方案名称
  - 给药途径（注射、口服、舌下、凝胶、贴片）
  - 药物类型（E2、EV、EC、EEn、EU）
  - 剂量（mg）
  - 是否启用

- **给药周期类型**：
  - **每天**：每天固定时间给药
  - **每周**：每周特定几天给药
  - **自定义**：自定义间隔天数

- **时间配置**：
  - 支持多个时间点（如每天8:00和23:30）
  - 星期选择（用于每周模式）
  - 间隔天数（用于自定义模式）

#### UI界面

- **用药方案列表页面** ([MedicationPlansScreen.kt](app/src/main/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreen.kt))
  - 显示所有用药方案
  - 方案卡片显示：名称、描述、启用状态
  - 支持快速启用/禁用方案
  - 点击卡片编辑方案

- **添加/编辑方案弹窗** ([MedicationPlanBottomSheet.kt](app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationPlanBottomSheet.kt))
  - 方案名称输入
  - 给药途径选择
  - 药物类型选择
  - 剂量输入
  - 给药周期类型选择
  - 时间配置（支持添加多个时间点）
  - 星期选择（每周模式）
  - 间隔天数输入（自定义模式）

#### 导航

- 在底部导航栏添加了"方案"标签
- 使用医疗服务图标（MedicalServices）

### 2. 用药提醒功能

#### 提醒管理器 (`ReminderManager`)

- **功能**：
  - 为启用的用药方案自动设置提醒
  - 使用Android AlarmManager实现精确闹钟
  - 支持提前设置接下来7天的提醒
  - 自动取消已禁用方案的提醒

- **提醒计算**：
  - 根据方案类型计算下次提醒时间
  - 支持每天、每周、自定义间隔三种模式
  - 智能跳过已过去的时间

#### 通知系统 (`NotificationHelper`)

- **通知特性**：
  - 通知渠道配置（高优先级）
  - 显示方案名称和描述
  - 点击通知打开应用
  - 自动取消通知

#### 广播接收器 (`MedicationReminderReceiver`)

- 接收定时提醒广播
- 触发通知显示

### 3. 未来血药浓度预测

#### 预测工具 (`MedicationPlanPredictor`)

- **功能**：
  - 根据用药方案生成未来的虚拟DoseEvent
  - 支持多个方案的组合预测
  - 默认预测15天

- **预测算法**：
  - 根据给药周期类型生成相应的事件序列
  - 每天模式：每天所有时间点
  - 每周模式：指定星期的所有时间点
  - 自定义模式：按间隔天数生成

#### 集成到模拟引擎

- **HRTViewModel增强**：
  - 监听用药方案变化
  - 合并历史用药记录和未来预测事件
  - 运行药代动力学模拟
  - 模拟时间范围：当前时刻前15天到后15天

- **图表显示**：
  - 现有的`ConcentrationChart`自动显示预测曲线
  - 用当前时刻标记（大圆点）区分历史和未来
  - 给药时间点用虚线标记

## 技术实现

### 数据层

1. **数据库**：
   - 添加`MedicationPlanEntity`表
   - 使用Room ORM
   - 支持类型转换（List、Set、Map等）

2. **Repository**：
   - `MedicationPlanRepository`：方案CRUD操作
   - 使用Flow实现响应式数据流

3. **ViewModel**：
   - `MedicationPlanViewModel`：管理方案逻辑
   - `HRTViewModel`：集成预测功能
   - 自动更新提醒和模拟

### UI层

1. **Compose界面**：
   - 使用Material 3设计
   - 响应式布局
   - 表单验证

2. **交互**：
   - 底部弹窗（ModalBottomSheet）
   - 时间选择器（TimePicker）
   - 分段按钮（SegmentedButton）
   - 筛选芯片（FilterChip）

### 系统集成

1. **权限**：
   - `POST_NOTIFICATIONS`（Android 13+）
   - `SCHEDULE_EXACT_ALARM`（Android 12+）
   - `USE_EXACT_ALARM`

2. **组件注册**：
   - 在AndroidManifest.xml中注册BroadcastReceiver

## 使用方法

### 创建用药方案

1. 点击底部导航栏的"方案"标签
2. 点击右下角的"+"按钮
3. 填写方案信息：
   - 输入方案名称（如"EV注射"）
   - 选择给药途径
   - 选择药物类型
   - 输入剂量
   - 选择给药周期类型
   - 配置给药时间
4. 点击"保存"

### 管理用药方案

- **启用/禁用**：点击方案卡片上的开关
- **编辑**：点击方案卡片进入编辑
- **删除**：在编辑页面点击"删除"按钮

### 查看预测

- 返回主页查看血药浓度图表
- 当前时刻用大圆点标记
- 当前时刻之后的曲线即为预测数据
- 图表自动包含未来15天的预测

## 文件结构

```
app/src/main/java/io/github/yuninggu/evolune/
├── data/
│   ├── MedicationPlan.kt                # 用药方案数据模型
│   ├── MedicationPlanEntity.kt          # 数据库实体
│   ├── MedicationPlanDao.kt             # DAO接口
│   └── MedicationPlanRepository.kt      # 数据仓库
├── reminder/
│   ├── NotificationHelper.kt            # 通知助手
│   ├── MedicationReminderReceiver.kt    # 广播接收器
│   └── ReminderManager.kt               # 提醒管理器
├── utils/
│   └── MedicationPlanPredictor.kt       # 预测工具
├── viewmodel/
│   ├── MedicationPlanViewModel.kt       # 方案ViewModel
│   └── HRTViewModel.kt                  # 更新：集成预测
├── ui/
│   ├── screens/
│   │   └── MedicationPlansScreen.kt     # 方案列表页面
│   └── components/
│       ├── MedicationPlanCard.kt        # 方案卡片组件
│       └── MedicationPlanBottomSheet.kt # 方案编辑弹窗
└── navigation/
    ├── Screen.kt                        # 更新：添加方案路由
    └── AppNavigation.kt                 # 更新：添加方案导航
```

## 注意事项

1. **权限请求**：
   - 应用首次运行时需要请求通知权限（Android 13+）
   - 需要精确闹钟权限（Android 12+）

2. **提醒限制**：
   - 提前设置接下来7天的提醒
   - 应用启动时自动重新设置所有提醒
   - 方案变更时自动更新提醒

3. **预测准确性**：
   - 预测基于当前配置的方案
   - 修改方案后会立即更新预测
   - 预测不考虑未来可能的方案变更

4. **性能考虑**：
   - 模拟包含历史和未来数据
   - 时间范围：前15天到后15天（共30天）
   - 步长：15分钟

## 未来改进建议

1. **提醒功能增强**：
   - 支持提前提醒（如提前15分钟）
   - 自定义提醒音
   - 支持重复提醒

2. **预测功能增强**：
   - 可配置预测时长
   - 显示预测置信区间
   - 对比多个方案的预测效果

3. **UI/UX改进**：
   - 方案复制功能
   - 方案模板
   - 批量操作
   - 方案历史记录

4. **数据分析**：
   - 用药依从性统计
   - 血药浓度达标率
   - 方案效果评估

## 相关文档

- [快速开始指南](QUICK_START_GUIDE.md)
- [UI组件文档](UI_COMPONENTS.md)
- [PK实现文档](PK_IMPLEMENTATION.md)
