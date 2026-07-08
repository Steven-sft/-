# 驾驶记录仪 - iOS 版

## 功能

- 🛰️ **GPS 高频采集**：5Hz（200ms 间隔）经纬度、速度、航向角、海拔
- 📱 **传感器采集**：加速度计 50Hz 采样 + 低通滤波
- 🧠 **行为检测**：变道（三信号融合）、急刹车、急转弯、急加速
- 🗺️ **卫星地图**：MapKit 卫星影像 + 实时轨迹 + 事件标记
- 📤 **数据导出**：CSV + JSON 一键分享

## 系统要求

- iOS 17.0+
- Xcode 15.0+
- 真机测试（模拟器不支持 GPS/传感器）

## 构建（3 种方式）

### 方式一：☁️ GitHub Actions 云构建（免费，无需 Mac）
1. 把本项目推送到 GitHub 仓库
2. 进入 Actions → Build iOS IPA → Run workflow
3. 等待约 5 分钟，下载 IPA 产物
4. 用 **AltStore** 或 **Sideloadly** 安装到 iPhone（免费，7 天重签一次）

### 方式二：💻 Mac + Xcode（推荐）
1. Xcode 打开 `DrivingRecorder-iOS` 文件夹
2. 运行 `xcodegen generate --spec project.yml` 生成项目
3. 选择开发者团队、连接 iPhone
4. ⌘R 运行

### 方式三：💲 苹果开发者账号（$99/年）
- 可永久签名、上架 App Store
- 配合 GitHub Actions 实现 CI/CD 自动构建发布

## 未签名 IPA 安装

下载 GitHub Actions 构建的 IPA 后：
1. 安装 **AltStore**（[altstore.io](https://altstore.io)）或 **Sideloadly**（[sideloadly.io](https://sideloadly.io)）
2. 用 Apple ID 签名（免费，7 天有效期）
3. 将 IPA 安装到 iPhone

## 权限

首次启动会依次请求：
1. 位置权限（选择「始终允许」以支持后台录制）
2. 运动传感器权限

## 项目结构

```
DrivingRecorder-iOS/DrivingRecorder/
├── DrivingRecorderApp.swift     # @main 入口
├── ContentView.swift            # 主界面（4标签页）
├── Models/                      # 数据模型
│   ├── Trip.swift
│   ├── DataPoint.swift
│   ├── DrivingEvent.swift
│   └── EventType.swift
├── Services/                    # 核心服务
│   ├── LocationService.swift    # CoreLocation GPS
│   ├── MotionService.swift      # CoreMotion 加速度计
│   ├── DetectionEngine.swift    # 行为检测算法
│   ├── RecordingService.swift   # 录制编排
│   └── ExportService.swift      # CSV/JSON 导出
├── Storage/
│   └── DataStore.swift          # JSON 文件持久化
├── Views/                       # UI 视图
│   ├── MapTabView.swift         # 卫星地图 + 轨迹
│   ├── DashboardView.swift      # 仪表盘
│   ├── EventsListView.swift     # 事件列表
│   ├── ExportView.swift         # 导出界面
│   ├── TripHistoryView.swift    # 历史记录
│   └── Components/
│       ├── SpeedGaugeView.swift # 速度表盘
│       └── EventRowView.swift   # 事件行
└── Utils/
    ├── MathUtils.swift          # 低通滤波、Haversine
    └── Formatters.swift         # 日期/距离格式化
```
