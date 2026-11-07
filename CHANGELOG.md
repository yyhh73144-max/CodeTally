<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CodeTally Changelog

## [Unreleased]

## [0.0.1] - 2026-11-07

### Added

- 按文件类型（Kotlin、Java、C/C++、XML）统计文件数、总行数、代码行、注释行、空行与函数数，并给出全部合计
- 统计范围：整个项目，或在项目视图中右键统计所选目录/文件
- “代码统计”工具窗口：按语言汇总表 + 文件明细表，支持排序、按语言筛选、双击打开文件
- 数字格式切换：原始 / 千分位 / 中文（万/亿），悬停显示精确值
- 自动排除库、生成源码、Gradle `build` 输出与所有隐藏缓存目录（`.gradle`、`.gradle-user`、`.idea`、`.cxx` 等）
- 项目级自定义排除文件夹：设置页（Settings › Tools › CodeTally 代码统计）与项目视图右键菜单，保存在 `.idea/codetally.xml`
- 函数数基于轻量词法分析，不依赖语言插件，未安装 C/C++ 支持时同样可用

[Unreleased]: https://github.com/yyhh73144-max/CodeTally/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/yyhh73144-max/CodeTally/commits/v0.0.1
