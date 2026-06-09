package tests;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.TestUtil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstPluginTest {

    private static final Logger log = LoggerFactory.getLogger(ConstPluginTest.class);

    private static void logCompilationResult(String testName, boolean success, TestUtil.TaskResult taskResult) {
        log.info("=== {} ===", testName);
        log.info("Compilation success: {}", success);

        String compilerOutput = taskResult.output().toString();
        if (!compilerOutput.isBlank()) {
            log.info("Compiler output:\n{}", compilerOutput);
        }

        if (!success) {
            log.info("Diagnostics:");
            taskResult.diagnostics().getDiagnostics().forEach(d ->
                    log.info("  {}: {}", d.getKind(), d.getMessage(null))
            );
        }
    }

    @Test
    public void testConstInvocationRule() throws Exception {

        String source =
                """
                package test;

                import com.hirshi001.constcheck.Const;

                class A {
                    void setX(int x) {}

                    @Const
                    int getX() { return 1; }
                }

                class B {
                    void run() {
                        A a = new A();
                        @Const A constRef = a;
                        constRef.setX(3);
                        constRef.getX();
                    }
                }
                """;

      
        try(var taskResult = TestUtil.compile(source)) {
                Boolean success = taskResult.task().call();

                logCompilationResult("testConstInvocationRule", success, taskResult);

                assertFalse(success, "Compilation should fail when @Const variable calls non-@Const method");
                assertTrue(
                        taskResult.diagnostics().getDiagnostics().stream()
                                .anyMatch(d -> d.getMessage(null).contains("Cannot call non-@Const method on a @Const variable")),
                        "Expected plugin diagnostic was not emitted"
                );
        }
    }

    @Test
    public void testConstArgumentToNonConstParameterFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.Const;

                class Box {
                    int value;
                    void setValue(int value) { this.value = value; }
                }

                class A {
                    void consume(Box value) {}

                    void run() {
                        @Const Box box = new Box();
                        consume(box);
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstArgumentToNonConstParameterFails", success, taskResult);

            assertFalse(success, "Compilation should fail when @Const argument is passed to non-@Const parameter");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("@Const value passed to non-@Const parameter")),
                    "Expected @Const parameter mismatch diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testNonConstArgumentToNonConstParameterPasses() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.Const;

                class Box {
                    int value;
                    void setValue(int value) { this.value = value; }
                }

                class A {
                    void consume(Box value) {}

                    void run() {
                        Box box = new Box();
                        box.setValue(10);
                        consume(box);
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testNonConstArgumentToNonConstParameterPasses", success, taskResult);

            assertTrue(success, "Compilation should succeed when non-@Const argument is passed to non-@Const parameter");
        }
    }

    @Test
    public void testConstFunDoesNotImplyConstReturnValue() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.Const;
                import com.hirshi001.constcheck.ConstFun;

                class WrapperInt {
                    public int value;

                    public WrapperInt(int value) {
                        this.value = value;
                    }

                    @ConstFun
                    public Integer getValue() {
                        return value;
                    }

                    public void setValue(int value) {
                        this.value = value;
                    }
                }

                class Main {
                    public static void main(String[] args) {
                        WrapperInt w = new WrapperInt(10);
                        w.setValue(20);
                        System.out.println(w.getValue());

                        @Const WrapperInt b = w;
                        System.out.println(b.getValue());
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunDoesNotImplyConstReturnValue", success, taskResult);

            assertTrue(success, "Compilation should succeed: @ConstFun does not mark return value as @Const");
        }
    }

    @Test
    public void testAssignConstReturnToNonConstVariableFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.Const;
                import com.hirshi001.constcheck.ConstFun;

                class Box {
                    int value;
                    Box(int value) { this.value = value; }
                }

                class Factory {
                    @ConstFun
                    @Const
                    Box makeConstBox() {
                        return new Box(1);
                    }
                }

                class Main {
                    void run() {
                        Factory f = new Factory();
                        Box mutable = f.makeConstBox();
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testAssignConstReturnToNonConstVariableFails", success, taskResult);

            assertFalse(success, "Compilation should fail when assigning @Const value to non-@Const variable");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("Cannot assign @Const value to non-@Const variable")),
                    "Expected assignment diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testStaticConstFunMethodFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class A {
                    @ConstFun
                    static int badStaticConstFun() {
                        return 1;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testStaticConstFunMethodFails", success, taskResult);

            assertFalse(success, "Compilation should fail for static methods annotated with @ConstFun");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("@ConstFun methods must be non-static")),
                    "Expected static @ConstFun diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testPrimitiveConstFunMethodFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class A {
                    @ConstFun
                    int badPrimitiveConstFun() {
                        return 1;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testPrimitiveConstFunMethodFails", success, taskResult);

            assertFalse(success, "Compilation should fail for @ConstFun methods with primitive return type");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("@ConstFun methods cannot have a primitive return type")),
                    "Expected primitive @ConstFun diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testConstFunFieldAssignmentFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    Integer value;

                    @ConstFun
                    Integer getValue() {
                        this.value = 1;
                        return value;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunFieldAssignmentFails", success, taskResult);

            assertFalse(success, "Compilation should fail when assigning to a field inside a @ConstFun method");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("Cannot assign to a field inside a @ConstFun method")),
                    "Expected @ConstFun field assignment diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testConstFunMutFieldAssignmentPasses() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;
                import com.hirshi001.constcheck.Mut;

                class Wrapper {
                    @Mut
                    Integer value;

                    @ConstFun
                    Integer getValue() {
                        this.value = 1;
                        return value;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunMutFieldAssignmentPasses", success, taskResult);

            assertTrue(success, "Compilation should succeed when assigning to a @Mut field inside a @ConstFun method");
        }
    }

    @Test
    public void testConstFunLocalVariableAssignmentPasses() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    int value;

                    @ConstFun
                    Integer getValue() {
                        int local = value;
                        local = 1;
                        return local;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunLocalVariableAssignmentPasses", success, taskResult);

            assertTrue(success, "Compilation should succeed when assigning to a local variable inside a @ConstFun method");
        }
    }

    @Test
    public void testNonConstFunFieldAssignmentPasses() throws Exception {
        String source =
                """
                package test;

                class Wrapper {
                    int value;

                    void setValue(int value) {
                        this.value = value;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testNonConstFunFieldAssignmentPasses", success, taskResult);

            assertTrue(success, "Compilation should succeed when assigning to a field outside a @ConstFun method");
        }
    }

    @Test
    public void testConstFunSuperclassFieldAssignmentFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Base {
                    int baseValue;
                }

                class Derived extends Base {
                    @ConstFun
                    Integer readBase() {
                        super.baseValue = 1;
                        return baseValue;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunSuperclassFieldAssignmentFails", success, taskResult);

            assertFalse(success, "Compilation should fail when assigning to a superclass field inside a @ConstFun method");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("Cannot assign to a field inside a @ConstFun method")),
                    "Expected @ConstFun superclass field assignment diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testStaticMutFieldFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.Mut;

                class A {
                    @Mut
                    static int badStaticMutField;
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testStaticMutFieldFails", success, taskResult);

            assertFalse(success, "Compilation should fail for @Mut on a static field");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains("@Mut cannot be applied to static fields")),
                    "Expected static @Mut field diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testInstanceMutFieldPasses() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.Mut;

                class A {
                    @Mut
                    int value;
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testInstanceMutFieldPasses", success, taskResult);

            assertTrue(success, "Compilation should succeed for @Mut on an instance field");
        }
    }

    @Test
    public void testConstFunInternalNonConstFunCallFails() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    int value;

                    void setValue(int value) {
                        this.value = value;
                    }

                    @ConstFun
                    Integer readAndSet() {
                        setValue(1);
                        return value;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunInternalNonConstFunCallFails", success, taskResult);

            assertFalse(success, "Compilation should fail when a @ConstFun method calls a non-@ConstFun this method");
            assertTrue(
                    taskResult.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.getMessage(null).contains(
                                    "Cannot call non-@ConstFun instance method on this inside a @ConstFun method")),
                    "Expected @ConstFun internal call diagnostic was not emitted"
            );
        }
    }

    @Test
    public void testConstFunInternalConstFunCallPasses() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    int value;

                    @ConstFun
                    Integer getValue() {
                        return value;
                    }

                    @ConstFun
                    Integer readTwice() {
                        return getValue() + getValue();
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunInternalConstFunCallPasses", success, taskResult);

            assertTrue(success, "Compilation should succeed when a @ConstFun method calls another @ConstFun this method");
        }
    }

    @Test
    public void testConstFunCallOnOtherInstancePasses() throws Exception {
        String source =
                """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    int value;

                    void setValue(int value) {
                        this.value = value;
                    }

                    @ConstFun
                    Integer readOther(Wrapper other) {
                        other.setValue(1);
                        return other.value;
                    }
                }
                """;

        try (var taskResult = TestUtil.compile(source)) {
            Boolean success = taskResult.task().call();

            logCompilationResult("testConstFunCallOnOtherInstancePasses", success, taskResult);

            assertTrue(success, "Compilation should succeed when calling a non-@ConstFun method on another instance");
        }
    }
}
