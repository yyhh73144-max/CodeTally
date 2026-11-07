# CodeTally

![Build](https://github.com/yyhh73144-max/CodeTally/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Adjust the [group](./gradle.properties), as well as the [id](./src/main/resources/META-INF/plugin.xml), [name](./src/main/resources/META-INF/plugin.xml), and [sources package](./src/main/kotlin).
- [ ] Adjust the plugin [description](./src/main/resources/META-INF/plugin.xml) (see [Tips][docs:plugin-description]) and this README to describe what your plugin does.
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `MARKETPLACE_ID` in the above README badges. You can obtain it once the plugin is published to JetBrains Marketplace.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.

面向 Android Studio（Rabbit 2 | 2026.2.2，平台 262 起）的代码统计插件。

## 功能

- 按文件类型统计 **文件数、总行数、代码行、注释行、空行、函数数**，并给出“全部”合计：
  Kotlin（`.kt` `.kts`）、Java、C/C++（`.c` `.h` `.cpp` `.cc` `.cxx` `.hpp` `.hh` `.hxx` `.inl`）、XML。
- 统计范围：<kbd>Tools</kbd> > <kbd>统计整个项目代码</kbd>，或在项目视图中右键目录/文件 > <kbd>统计所选文件代码</kbd>。
- 底部“代码统计”工具窗口：按语言汇总表 + 文件明细表（可排序、按语言过滤，双击打开文件）。
- 自动排除库、excluded 目录、生成源码、`.gradle`/`.idea`，以及 Gradle 模块下的 `build`/`.cxx`/`.externalNativeBuild`；
  跳过超过 5 MB 的文件；未保存的编辑内容按编辑器中的文本统计。

## 统计口径

- 空行：只含空白字符的行（即使位于注释或多行字符串内）。
- 代码行：含任意代码字符的行（代码后带行尾注释仍算代码行）。
- 注释行：非空且只含注释的行。
- 函数数为轻量词法分析的估算，不依赖 IDE 语言插件：
  - Kotlin：`fun` 声明（含成员、扩展、局部、抽象、匿名函数）与次构造函数；不含 lambda、`fun interface`。
  - Java：方法与构造器（含抽象/接口/注解成员方法、匿名类与局部类中的方法）。
  - C/C++：只统计带函数体的定义（原型不计，避免头文件重复计数）；宏生成的函数可能偏差。
  - XML：不适用（显示为 “—”）。

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "CodeTally"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/yyhh73144-max/CodeTally/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
