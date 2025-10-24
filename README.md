# My Application - 待办事项管理应用

一款功能强大的 Android 待办事项管理应用，具备消息推送、任务管理、后台服务保活等特性。

## 项目概览

这是一个基于 Android 原生开发的任务管理应用，采用 WebView + 本地数据库的混合架构，提供完整的任务创建、编辑、完成和消息通知功能。

### 主要特性

- **任务管理**
  - 创建、编辑、完成任务
  - 任务分配与协作
  - 每日待办任务自动生成
  - 任务完成记录与图片附件

- **消息通知系统**
  - 实时消息监听服务
  - 任务完成通知
  - 任务分配通知
  - 系统消息推送
  - 可展开的详细通知内容

- **用户偏好设置**
  - 消息类型过滤
  - 发送者白名单/黑名单
  - 工作时间设置
  - 通知样式自定义（声音、震动、LED）
  - 消息预览与自动已读

- **后台服务保活**
  - 开机自启动
  - 前台服务运行
  - 电池优化白名单管理
  - 定时唤醒机制
  - 数据定时清理

- **WebView 集成**
  - 本地 HTML5 界面
  - JavaScript 与原生代码交互
  - 文件上传支持
  - 通知 API 桥接

## 技术栈

- **开发语言**: Java
- **构建工具**: Gradle (Kotlin DSL)
- **最低 SDK**: Android 14 (API 34)
- **目标 SDK**: Android 14 (API 36)
- **数据库**: SQLite
- **UI**: WebView + HTML5

## 项目结构

```
MyApplication/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/myapplication/
│   │       │   ├── MainActivity.java              # 主Activity
│   │       │   ├── TaskEditActivity.java          # 任务编辑页面
│   │       │   ├── TaskCompleteActivity.java      # 任务完成页面
│   │       │   ├── MessageListenerService.java    # 消息监听服务
│   │       │   ├── CleanupService.java            # 数据清理服务
│   │       │   ├── DatabaseHelper.java            # 数据库操作类
│   │       │   ├── SupabaseInterface.java         # 数据库接口
│   │       │   ├── DatabaseInterface.java         # 数据库接口定义
│   │       │   ├── NotificationHelper.java        # 通知辅助类
│   │       │   ├── ServiceKeepAliveManger.java    # 服务保活管理
│   │       │   ├── ServiceWakeupReceiver.java     # 服务唤醒接收器
│   │       │   ├── BootReceiver.java              # 开机启动接收器
│   │       │   └── ServiceUtils.java              # 服务工具类
│   │       ├── assets/                            # WebView 资源文件
│   │       └── AndroidManifest.xml                # 应用配置清单
│   └── build.gradle.kts                           # 应用构建配置
├── build.gradle.kts                               # 项目构建配置
├── settings.gradle.kts                            # 项目设置
└── README.md                                      # 项目说明文档
```

## 核心功能模块

### 1. 数据库模块 (DatabaseHelper)

使用 SQLite 存储本地数据，包含两张主表：

- **messages 表**: 存储消息记录
  - 发送者/接收者信息
  - 任务关联
  - 消息类型（完成/分配/系统）
  - 已读状态

- **user_settings 表**: 存储用户偏好设置
  - 通知接收偏好
  - 白名单/黑名单
  - 工作时间设置

### 2. 消息监听服务 (MessageListenerService)

- 前台服务方式运行
- 定时检查新消息
- 根据用户设置过滤消息
- 触发系统通知

### 3. 后台保活机制

- **开机自启动**: `BootReceiver` 监听系统启动广播
- **服务唤醒**: `ServiceWakeupReceiver` 定时唤醒服务
- **电池优化**: 引导用户将应用加入白名单
- **前台服务**: 使用通知保持服务存活

### 4. WebView 桥接

通过 `@JavascriptInterface` 实现 JavaScript 与原生代码交互：

- `AndroidNotification`: 通知接口
- `AndroidDatabase`: 数据库操作接口

## 权限说明

应用需要以下权限：

- `POST_NOTIFICATIONS`: 发送通知
- `FOREGROUND_SERVICE`: 前台服务
- `RECEIVE_BOOT_COMPLETED`: 开机自启动
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: 电池优化豁免
- `SCHEDULE_EXACT_ALARM`: 精确闹钟
- `INTERNET`: 网络访问
- `CAMERA`: 相机（任务图片附件）
- `READ_MEDIA_*`: 媒体文件访问

## 构建与运行

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 11
- Android SDK 34+

### 构建步骤

1. 克隆项目到本地
```bash
git clone <repository-url>
cd MyApplication
```

2. 使用 Android Studio 打开项目

3. 等待 Gradle 同步完成

4. 连接 Android 设备或启动模拟器

5. 运行应用
```bash
./gradlew assembleDebug
```

### APK 输出

构建后的 APK 文件命名格式：`TODO_yyyyMMdd_HHmm.apk`

位置：`app/build/outputs/apk/debug/`

## 配置说明

### 修改应用包名

在 `app/build.gradle.kts` 中修改：
```kotlin
android {
    namespace = "com.example.myapplication"
    defaultConfig {
        applicationId = "com.example.myapplication"
    }
}
```

### 修改版本信息

```kotlin
defaultConfig {
    versionCode = 1
    versionName = "5"
}
```

## 使用说明

### 首次启动

1. 授予必要权限（通知、文件访问等）
2. 系统会提示将应用加入电池优化白名单
3. 设置工作时间和通知偏好

### 任务管理

- 在 WebView 界面创建和编辑任务
- 完成任务时可添加备注和图片
- 系统自动生成每日待办任务

### 消息通知

- 任务完成后自动通知相关人员
- 点击通知查看详细内容
- 可在设置中调整通知行为

## 注意事项

- 首次安装需要手动授予所有权限
- 建议将应用加入电池优化白名单以确保后台运行
- 部分手机厂商需要额外设置自启动权限
- WebView 需要加载本地 `index.html` 文件

## 已知问题

- 某些设备上后台服务可能被系统清理
- 需要用户手动处理特定厂商的权限限制

## 开发计划

- [ ] 添加云同步功能
- [ ] 支持多语言
- [ ] 优化电池使用
- [ ] 添加数据导出功能
- [ ] 改进 UI/UX

## 许可证

本项目仅供学习和研究使用。

## 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发送邮件至：[your-email@example.com]

---

**版本**: v5
**最后更新**: 2025-10-24
