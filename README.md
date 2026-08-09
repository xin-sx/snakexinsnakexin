# TimeTracker (时间记录)

一个超轻量的 Android 工具：用「姓名 + 时间」记录人生里那些重要的节点，并在主页面随时看到每个节点距离今天已经过了多少年/月/日。

## 主要功能

- 主页顶部实时显示当前时间（年月日秒），每秒钟刷新。
- 每条资料是一个长方形卡片：上面窄框是姓名，下面是时间。
  - 如果在编辑时没有选时分秒，资料卡上只显示 `yyyy-MM-dd`。
  - 选过时/分/秒，则显示完整的 `yyyy-MM-dd HH:mm:ss`。
- 点击主页右下角 `+` 按钮新增资料；点击任意卡片进入详情页。
- 详情页：
  - 顶部：姓名 + 输入的时间 + 当前的 `HH:mm:ss`（每秒钟刷新）。
  - 下方：「X 年 Y 月 Z 天」差 + 合计天数。
- 数据通过 `SharedPreferences` + JSON 持久化。

## 适配

- 64 位 ABI：`arm64-v8a`、`x86_64`（`abiFilters` 在 `app/build.gradle` 里设置）。
- `minSdk = 24`（Android 7.0），`targetSdk = compileSdk = 36`（Android 16）。
- 使用 Material Components 主题，UI 简洁、易适配深色/浅色系统主题。

## 截图占位

主页：
> 顶部实时时间；下方 3 个长方形资料卡（姓名 + 时间）；最下方 `+` 按钮。

详情页：
> 顶部：姓名 + 用户输入时间 + 当前时分秒；
> 下方：距今的年/月/日差 + 合计天数。

## 构建

```bash
# 1) 配置好 ANDROID_HOME
export ANDROID_HOME=/path/to/android-sdk

# 2) 编译 debug + release APK
gradle assembleDebug assembleRelease
```

产物：
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`（需要自行签名）

## 包名

`com.demo.snakexin`

## 开源协议

仅作为学习/演示用途。
