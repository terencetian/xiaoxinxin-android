# 小薪薪 · Android 版

Mac 悬浮小窗「小薪薪」的手机版。填月薪、打卡，屏幕上实时显示今天已经挣了多少钱。

## 与 macOS 版的差异

手机上没有「常驻小窗」这个概念，所以形态换了：

| 能力 | Android | macOS |
|---|---|---|
| 屏幕悬浮窗 | ✅ 可拖动、双击收起 | ✅ 悬浮小窗 |
| 状态栏 / 通知栏常驻 | ✅ 通知栏显示实时金额 | ✅ 菜单栏 |
| 桌面小组件 | ⬜ 待做 | ✅ |
| 贴边收纳 | ❌ 系统不支持 | ✅ |
| 图标吸色 | ⬜ 待做 | ✅ |

数据目前各端独立，不互通（`EarnState.deviceId` 已预留，日后接同步不用改算法）。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 35、build-tools 35.0.0）。

```bash
# debug 包
./gradlew assembleDebug

# 正式签名包（需 keystore.properties）
./gradlew assembleRelease
```

若本机没有 Gradle，可用项目外置的：
`$HOME/dev-tools/gradle-8.11.1/bin/gradle assembleRelease`

## 签名

`keystore.properties`（不入库）内容：

```
storeFile=keystore/xiaoxinxin.jks
storePassword=****
keyAlias=xiaoxinxin
keyPassword=****
```

**keystore 与密码务必另存备份**。丢失后无法再给已安装用户升级，只能换包名重来。

## 归档

```bash
./tools/archive_apk.sh 1.9.3 --rebuild
```

会校验 APK 内的 versionName 与传入版本一致（不符直接中止），复制到
`~/Documents/小薪薪-发布版本/v<版本>/`，打印 sha256，并在 `版本索引.md` 追加一行。

## 测试

```bash
./gradlew testDebugUnitTest
```

逻辑层（工作日推算、跨零点班次、日期校验、费率）与 Swift 版断言一一对应。

## 结构

```
app/src/main/java/com/haofan/xiaoxinxin/
├── core/       纯逻辑层，无 Android 依赖，可单测
│   ├── Logic.kt     工时/节假日/日期校验/费率
│   ├── EarnState.kt 状态与金额计算（金额由时间戳现算，不靠定时器累加）
│   └── EarnRepo.kt  持久化 + 打卡/跨天/自动下班/流水
├── ui/         Compose 界面（Liquid Glass 风格）
└── floatx/     悬浮窗前台服务
```
