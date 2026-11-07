package com.github.yyhh73144max.codetally.toolWindow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CountFormat] 数字显示格式测试。
 */
class CountFormatTest {

    /** 验证中文格式：小于一万原样显示，整万不带小数（40000 → 4万），其余保留至多两位小数并去掉末尾 0。 */
    @Test
    fun chineseUsesWanAndYi() {
        val f = CountFormat.CHINESE
        assertEquals("0", f.format(0))
        assertEquals("9999", f.format(9_999))
        assertEquals("1万", f.format(10_000))
        assertEquals("4万", f.format(40_000))
        assertEquals("1.5万", f.format(15_000))
        assertEquals("60.93万", f.format(609_272))
        assertEquals("1.23万", f.format(12_345))
        assertEquals("1亿", f.format(100_000_000))
        assertEquals("2.35亿", f.format(234_567_890))
    }

    /** 验证四舍五入进位到上一级单位时改用上一级（99999999 不显示为“10000万”），回归风险：出现不规范单位。 */
    @Test
    fun chineseCarriesIntoYi() {
        assertEquals("1亿", CountFormat.CHINESE.format(99_999_999))
        assertEquals("10万", CountFormat.CHINESE.format(99_999))
    }

    /** 验证负数按绝对值格式化并保留负号（计数理论上非负，此处防御异常输入）。 */
    @Test
    fun chineseHandlesNegative() {
        assertEquals("-4万", CountFormat.CHINESE.format(-40_000))
    }

    /** 验证原始与千分位格式，千分位使用与系统区域无关的逗号分隔。 */
    @Test
    fun plainAndGrouped() {
        assertEquals("609272", CountFormat.PLAIN.format(609_272))
        assertEquals("609,272", CountFormat.GROUPED.format(609_272))
        assertEquals("999", CountFormat.GROUPED.format(999))
    }
}
