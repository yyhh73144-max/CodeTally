package com.github.yyhh73144max.codetally

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

/** 资源包路径。 */
@NonNls
private const val BUNDLE = "messages.CodeTallyBundle"

/**
 * 插件文案资源包，所有面向用户的字符串都从这里读取（文件以 UTF-8 保存）。
 */
object CodeTallyBundle : DynamicBundle(BUNDLE) {

    /**
     * 按键读取文案并格式化参数。
     *
     * @param key 资源键，编译期由 [PropertyKey] 校验存在。
     * @param params `MessageFormat` 参数。
     * @return 格式化后的文案。
     */
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
