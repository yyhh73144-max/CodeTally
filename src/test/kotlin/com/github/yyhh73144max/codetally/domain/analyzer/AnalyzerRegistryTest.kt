package com.github.yyhh73144max.codetally.domain.analyzer

import com.github.yyhh73144max.codetally.domain.model.LanguageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AnalyzerRegistry] 的路由测试。
 */
class AnalyzerRegistryTest {

    /** 验证各语言扩展名路由正确，且大小写不敏感（如 `Foo.KT`、`a.CPP`）。 */
    @Test
    fun routesExtensionsCaseInsensitively() {
        val registry = AnalyzerRegistry.DEFAULT
        assertEquals(LanguageKind.KOTLIN, registry.forExtension("kt")?.language)
        assertEquals(LanguageKind.KOTLIN, registry.forExtension("KTS")?.language)
        assertEquals(LanguageKind.JAVA, registry.forExtension("Java")?.language)
        assertEquals(LanguageKind.C_CPP, registry.forExtension("h")?.language)
        assertEquals(LanguageKind.C_CPP, registry.forExtension("CPP")?.language)
        assertEquals(LanguageKind.XML, registry.forExtension("xml")?.language)
    }

    /** 验证不支持的扩展名与无扩展名返回 null，调用方据此忽略文件。 */
    @Test
    fun unknownExtensionsAreIgnored() {
        assertNull(AnalyzerRegistry.DEFAULT.forExtension("txt"))
        assertNull(AnalyzerRegistry.DEFAULT.forExtension(null))
        assertNull(AnalyzerRegistry.DEFAULT.forExtension(""))
    }

    /** 验证同一扩展名被重复注册时立即失败，防止路由结果依赖注册顺序。 */
    @Test(expected = IllegalArgumentException::class)
    fun duplicateRegistrationFails() {
        AnalyzerRegistry(listOf(KotlinAnalyzer(), KotlinAnalyzer()))
    }
}
