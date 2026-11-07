# CodeTally

![Build](https://github.com/yyhh73144-max/CodeTally/workflows/Build/badge.svg)
[![Release](https://img.shields.io/github/v/release/yyhh73144-max/CodeTally)](https://github.com/yyhh73144-max/CodeTally/releases/latest)

**CodeTally** 是一款 Android Studio 代码统计插件：按文件类型统计 **文件数、总行数、代码行、注释行、空行与函数数**，
支持整个项目或任意目录，自动排除构建产物与缓存，并可按项目自定义排除文件夹。

*A code statistics plugin for Android Studio: counts files, total/code/comment/blank lines and functions per file type
(Kotlin, Java, C/C++, XML) for the whole project or any directory.*

## 功能亮点

- **多语言统计**：Kotlin（`.kt` `.kts`）、Java、C/C++（`.c` `.h` `.cpp` `.cc` `.cxx` `.hpp` `.hh` `.hxx` `.inl`）、XML，并给出“全部”合计。
- **函数数统计**：轻量词法分析，不依赖 IDE 语言插件——未安装 C/C++ 支持时也能统计 NDK/JNI 代码。
- **灵活范围**：统计整个项目，或在项目视图中右键任意目录/文件单独统计。
- **智能排除**：自动跳过库、生成源码、Gradle `build` 输出以及所有隐藏缓存目录（`.gradle`、`.gradle-user`、`.idea`、`.cxx` 等）。
- **项目级自定义排除**：在设置页或右键菜单中排除 `third_party` 等文件夹；配置保存在 `.idea/codetally.xml`，可随项目提交共享。
- **清晰的结果视图**：底部“代码统计”工具窗口提供按语言汇总表与文件明细表，支持排序、按语言筛选、双击打开文件。
- **中文数字格式**：可在原始 / 千分位 / 中文（万/亿）之间切换，例如 `40000` 显示为 `4万`，悬停查看精确值。
- **不打扰编辑**：统计在后台进行、可随时取消；未保存的编辑内容按编辑器中的文本统计。

## 使用方法

| 操作 | 入口 |
| --- | --- |
| 统计整个项目 | <kbd>Tools</kbd> › <kbd>统计整个项目代码</kbd>，或“代码统计”工具窗口中的 ▶ 按钮 |
| 统计指定目录/文件 | 项目视图右键 › <kbd>统计所选文件代码</kbd> |
| 排除 / 取消排除文件夹 | 项目视图右键文件夹 › <kbd>在代码统计中排除</kbd> |
| 管理排除列表 | <kbd>Settings</kbd> › <kbd>Tools</kbd> › <kbd>CodeTally 代码统计</kbd>，或工具窗口中的 ⚙ 按钮 |
| 切换数字格式 | 工具窗口顶部“数字”下拉框 |

> 在项目视图中显式右键某个被排除的目录进行统计时，仍会统计其内容（显式选择优先）。

## 统计口径

- **空行**：只含空白字符的行（即使位于注释或多行字符串内）。
- **代码行**：含任意代码字符的行（代码后带行尾注释仍算代码行）。
- **注释行**：非空且只含注释的行。
- **函数数**（词法估算）：
  - Kotlin：`fun` 声明（含成员、扩展、局部、抽象、匿名函数）与次构造函数；不含 lambda 与 `fun interface`。
  - Java：方法与构造器（含抽象/接口/注解成员方法、匿名类与局部类中的方法）。
  - C/C++：只统计带函数体的定义，原型不计（避免头文件与实现重复计数）；宏生成的函数可能有偏差。
  - XML：不适用（显示为 “—”）。

## 安装

1. 从 [Releases](https://github.com/yyhh73144-max/CodeTally/releases/latest) 下载 `CodeTally-x.y.z.zip`（无需解压）。
2. Android Studio › <kbd>Settings</kbd> › <kbd>Plugins</kbd> › <kbd>⚙️</kbd> › <kbd>Install Plugin from Disk...</kbd>，选择该 zip。
3. 重启 IDE 后，在底部工具栏打开“代码统计”。

**兼容性**：Android Studio Rabbit 2 | 2026.2.2 及以上（平台 262+）。插件只依赖 IntelliJ 平台核心模块，同平台版本的 IntelliJ IDEA 也可使用。

## 开发

```bash
./gradlew test           # 单元测试与平台集成测试
./gradlew runIde         # 在沙箱 Android Studio 中运行插件
./gradlew buildPlugin    # 打包到 build/distributions/
./gradlew verifyPlugin   # 使用 Plugin Verifier 检查兼容性
```

源码结构（`src/main/kotlin/com/github/yyhh73144max/codetally/`）：

| 目录 | 职责 |
| --- | --- |
| `domain/` | 纯 Kotlin 的词法扫描器、各语言分析器与统计模型，无 IDE 依赖 |
| `data/` | 项目文件收集、文本读取（UTF-8）与项目级设置持久化 |
| `application/` | 统计流水线与任务生命周期服务 |
| `toolWindow/` `actions/` `settings/` | 工具窗口、菜单动作与设置页 |

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
