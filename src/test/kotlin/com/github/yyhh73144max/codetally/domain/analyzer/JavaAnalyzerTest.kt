package com.github.yyhh73144max.codetally.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [JavaAnalyzer] 的行分类与方法识别测试（纯 JUnit，不启动 IDE）。
 */
class JavaAnalyzerTest {
    /** 被测分析器。 */
    private val analyzer = JavaAnalyzer()

    /** 验证方法、构造器、抽象/接口方法计入，而方法体内的调用、控制语句、lambda 与字段初始化不计入。 */
    @Test
    fun countsMethodsButNotCallsOrStatements() {
        val source = """
            |public class Foo extends Bar implements Baz {
            |    private final List<String> items = new ArrayList<>();
            |    private int value = compute();
            |    private final Runnable task = () -> { run(); };
            |    @SuppressWarnings("unchecked")
            |    public Foo(int v) throws IOException {
            |        super(v);
            |        if (v > 0) { System.out.println(v); }
            |        for (int i = 0; i < v; i++) { call(i); }
            |    }
            |    Foo() { this(1); }
            |    public <T> List<T> generic(T t) { return null; }
            |    protected abstract void abstractOne();
            |    static { init(); }
            |    int[] arrayReturn() { return new int[] {1, 2}; }
            |}
            |interface Baz { void a(); default void b() {} }
        """.trimMargin()
        // Foo(int)、Foo()、generic、abstractOne、arrayReturn、a、b
        assertEquals(7, analyzer.analyze(source).functionCount)
    }

    /** 验证匿名类与局部类中的方法计入。 */
    @Test
    fun countsMethodsInAnonymousAndLocalClasses() {
        val source = """
            |class A {
            |    void outer() {
            |        Runnable r = new Runnable() {
            |            @Override public void run() {}
            |        };
            |        Comparator<String> c = new java.util.Comparator<String>() {
            |            public int compare(String x, String y) { return 0; }
            |        };
            |        class Local { void inner() {} }
            |    }
            |}
        """.trimMargin()
        // outer、run、compare、inner
        assertEquals(4, analyzer.analyze(source).functionCount)
    }

    /** 验证枚举常量（含带参数的）不计入，枚举方法、构造器与常量体内方法计入。 */
    @Test
    fun handlesEnums() {
        val source = """
            |enum Op {
            |    PLUS("+") { int apply(int a, int b) { return a + b; } },
            |    MINUS("-") { int apply(int a, int b) { return a - b; } };
            |    private final String symbol;
            |    Op(String symbol) { this.symbol = symbol; }
            |    abstract int apply(int a, int b);
            |}
        """.trimMargin()
        // 两个常量体内的 apply、构造器、抽象 apply
        assertEquals(4, analyzer.analyze(source).functionCount)
    }

    /** 验证 record 头不被当作方法、注解类型成员计入、`Foo.class` 不会开启类型体。 */
    @Test
    fun handlesRecordsAnnotationsAndClassLiterals() {
        val source = """
            |record Point(int x, int y) {
            |    Point { if (x < 0) throw new IllegalArgumentException(); }
            |    int sum() { Class<?> c = Point.class; return x + y; }
            |}
            |@interface Marker { String value() default ""; int[] ids() default {}; }
        """.trimMargin()
        // sum、value、ids（紧凑构造器没有参数列表，不计入）
        assertEquals(3, analyzer.analyze(source).functionCount)
    }

    /** 验证文本块与字符串中的代码样式内容不影响统计，且文本块按代码行计。 */
    @Test
    fun textBlocksAreCode() {
        val source = "class A {\n    String s = \"\"\"\n        void fake() { // x\n        \\\"\"\" still\n        \"\"\";\n}"
        val result = analyzer.analyze(source)
        assertEquals(6, result.codeLines)
        assertEquals(0, result.commentLines)
        assertEquals(0, result.functionCount)
    }

    /** 验证注释中的方法样式文本不计入，注释行计数正确。 */
    @Test
    fun commentsAreIgnored() {
        val source = "class A {\n  // void a() {}\n  /* void b() {} */\n  void c() {}\n}"
        val result = analyzer.analyze(source)
        assertEquals(2, result.commentLines)
        assertEquals(3, result.codeLines)
        assertEquals(1, result.functionCount)
    }
}
