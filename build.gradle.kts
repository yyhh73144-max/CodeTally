import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // 目标：Android Studio Rabbit 2 | 2026.2.2（平台 262.10968.63，对应 IntelliJ 2026.2.3）
        androidStudio("2026.2.2.8")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 插件只依赖 com.intellij.modules.platform，最低支持 262 平台分支
            sinceBuild = "262"
        }
    }
}
