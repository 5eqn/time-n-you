# 时间观察者 (Time Watcher)

一个 Android 时间管理 / 番茄钟应用。点按切换当前状态（睡觉 / 生活 / 主业 / 爱好 / 小憩 / 摆烂），
自动记录每段状态的时长，并在主业 / 爱好 / 小憩上运行番茄钟（到点自动切换并响铃）。提供日 / 周 / 月 / 年统计。

- **核心**：每次状态切换都持久化为带时间戳的事件，所以**应用被关闭期间的时间也会被正确累计**。
- **番茄钟**：通过 `AlarmManager` + `BroadcastReceiver` 实现，**即便应用进程被杀死**，
  到点也会自动切换到退出状态（默认「摆烂」）并发送高优先级通知 + 响铃。
- **统计口径**：`利用率 = 有效时间 /（总时间 − 休息时间）`；
  有效 = 主业 + 爱好；休息 = 睡觉 + 生活 + 小憩；无效 = 摆烂。
  默认目标：利用率 ≥ 50% 且有效 > 6 小时为「优秀」（目标时间与比例均可在设置中修改；运动类被刻意排除以避免虚高）。

## 构建

需要 JDK 17 与 Android SDK（platform-tools、platforms;android-34、build-tools;34.0.0）。

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/seqn/Android/Sdk
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 统计/时间 纯逻辑单元测试
```

## 安装到设备 / 模拟器

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.seqn.timewatcher/.MainActivity
```

首次启动会请求通知权限（用于番茄钟响铃）。在 Android 12+ 上若精确闹钟权限被撤销，
会退化为非精确闹钟，并在下次打开应用时校正计时。

## 数据导出 / 导入

设置页可导出 / 导入 JSON（包含状态列表、番茄钟设置与全部切换日志）。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Room · AlarmManager · Navigation-Compose · Gson
