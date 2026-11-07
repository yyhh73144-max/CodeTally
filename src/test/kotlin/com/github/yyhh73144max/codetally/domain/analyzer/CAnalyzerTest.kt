package com.github.yyhh73144max.codetally.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CAnalyzer] 的行分类与函数定义识别测试（纯 JUnit，不启动 IDE）。
 */
class CAnalyzerTest {
    /** 被测分析器。 */
    private val analyzer = CAnalyzer()

    /** 验证只统计带函数体的定义，原型声明、函数指针、全局初始化与控制语句不计入。 */
    @Test
    fun countsDefinitionsNotPrototypes() {
        val source = """
            |#include <stdio.h>
            |int add(int a, int b);
            |static int (*fp)(int) = 0;
            |int table[] = { 1, 2, 3 };
            |struct Point { int x; int y; };
            |enum Color { RED, GREEN };
            |int add(int a, int b) {
            |    if (a > b) { return a; }
            |    for (int i = 0; i < b; i++) { printf("%d", i); }
            |    return a + b;
            |}
            |struct Point make_point(void) { struct Point p = { 0, 0 }; return p; }
            |int main(int argc, char **argv) { return add(1, 2); }
        """.trimMargin()
        assertEquals(3, analyzer.analyze(source).functionCount)
    }

    /** 验证预处理指令（含续行宏）计为代码行，宏体内的括号与花括号不干扰函数识别。 */
    @Test
    fun preprocessorDirectivesAreSkippedForFunctions() {
        val source = """
            |#define MAX(a, b) \
            |    ((a) > (b) ? (a) : (b))
            |#define BLOCK { int x = 0; }
            |#if defined(FOO)
            |#endif
            |void f(void) {}
        """.trimMargin()
        val result = analyzer.analyze(source)
        assertEquals(6, result.codeLines)
        assertEquals(1, result.functionCount)
    }

    /** 验证 C++ 类内方法、构造/析构函数、初始化列表、运算符重载、命名空间与类外定义都被识别。 */
    @Test
    fun countsCppMembers() {
        val source = """
            |namespace app {
            |class Widget : public Base {
            |public:
            |    Widget() : a_(0), b_{1}, c_(make()) {}
            |    ~Widget() override {}
            |    int get() const noexcept { return a_; }
            |    bool operator==(const Widget& o) const { return a_ == o.a_; }
            |    int operator()(int x) { return x; }
            |    void declared();
            |    virtual void pure() = 0;
            |    Widget(const Widget&) = default;
            |private:
            |    int a_;
            |};
            |void Widget::declared() { auto f = [](int x) { return x; }; }
            |auto trailing() -> std::vector<int> { return {}; }
            |template <typename T> T identity(T v) { return v; }
            |}
        """.trimMargin()
        // Widget()、~Widget、get、operator==、operator()、Widget::declared、trailing、identity
        assertEquals(8, analyzer.analyze(source).functionCount)
    }

    /** 验证 JNI 常见写法 `extern "C" JNIEXPORT ... JNICALL Java_xxx(...) {}` 与 extern "C" 块内定义被识别。 */
    @Test
    fun countsJniFunctions() {
        val source = """
            |extern "C" JNIEXPORT jstring JNICALL
            |Java_com_example_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
            |    std::string hello = "Hello from C++ {";
            |    return env->NewStringUTF(hello.c_str());
            |}
            |extern "C" {
            |    void helper() {}
            |}
        """.trimMargin()
        assertEquals(2, analyzer.analyze(source).functionCount)
    }

    /** 验证 C++ 原始字符串与数字分隔符不破坏词法状态。 */
    @Test
    fun rawStringsAndDigitSeparators() {
        val source = """
            |const char* s = R"x(void fake() { // )" not end
            |)x";
            |int big = 1'000'000;
            |void real() {}
        """.trimMargin()
        val result = analyzer.analyze(source)
        assertEquals(4, result.codeLines)
        assertEquals(0, result.commentLines)
        assertEquals(1, result.functionCount)
    }

    /** 验证无分号的宏调用不会把其后的类型定义误判为函数。 */
    @Test
    fun macroInvocationBeforeStructIsNotFunction() {
        val source = "DECLARE_THING(x)\nstruct S { int get() { return 1; } };"
        assertEquals(1, analyzer.analyze(source).functionCount)
    }
}
