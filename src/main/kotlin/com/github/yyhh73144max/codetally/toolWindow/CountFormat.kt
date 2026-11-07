package com.github.yyhh73144max.codetally.toolWindow

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 统计数字在工具窗口中的显示格式。只影响显示，表格排序始终基于原始整数。
 *
 * @property labelKey 下拉框中显示名称的资源键。
 */
enum class CountFormat(val labelKey: String) {
    /** 原样显示，例如 `609272`。 */
    PLAIN("format.plain"),

    /** 千分位分组，例如 `609,272`。 */
    GROUPED("format.grouped"),

    /** 中文万/亿单位，例如 `40000` 显示为 `4万`，`609272` 显示为 `60.93万`；小于一万原样显示。 */
    CHINESE("format.chinese"),
    ;

    /**
     * 按本格式把计数转换为显示文本。
     *
     * 中文格式保留至多两位小数（四舍五入）并去掉末尾的 0；若四舍五入后达到上一级单位（如 `99999999` → `10000万`），
     * 自动进位为上一级（`1亿`），避免出现“10000万”这类不规范写法。
     *
     * @param value 非负计数；负数按绝对值格式化并加负号。
     * @return 显示文本。
     */
    fun format(value: Long): String = when (this) {
        PLAIN -> value.toString()
        GROUPED -> String.format(Locale.ROOT, "%,d", value)
        CHINESE -> formatChinese(value)
    }

    /** 格式化器的单位常量与中文格式实现。 */
    private companion object {
        /** 一万。 */
        val WAN: BigDecimal = BigDecimal.valueOf(10_000L)

        /** 一亿。 */
        val YI: BigDecimal = BigDecimal.valueOf(100_000_000L)

        /** 中文单位格式化：小于一万原样输出，否则选用万或亿并保留两位小数。 */
        fun formatChinese(value: Long): String {
            if (value < 0) return "-" + formatChinese(-value)
            if (value < 10_000L) return value.toString()
            val number = BigDecimal.valueOf(value)
            if (number < YI) {
                val inWan = scaled(number, WAN)
                // 四舍五入后达到 10000 万即为 1 亿。
                if (inWan < WAN) return inWan.toPlainString() + "万"
            }
            return scaled(number, YI).toPlainString() + "亿"
        }

        /** 除以单位后保留两位小数并去掉末尾 0（`stripTrailingZeros` 后用 `toPlainString` 防止科学计数法）。 */
        fun scaled(number: BigDecimal, unit: BigDecimal): BigDecimal =
            number.divide(unit, 2, RoundingMode.HALF_UP).stripTrailingZeros()
    }
}
