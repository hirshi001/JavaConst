package tests;

import org.junit.jupiter.api.Test;
import util.TestUtil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstPluginTest {

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

                System.out.println("Compilation success: " + success);
                System.out.println("Compiler output:\n" + taskResult.output());

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
            assertTrue(success, "Compilation should succeed when non-@Const argument is passed to non-@Const parameter");
        }
    }
}
