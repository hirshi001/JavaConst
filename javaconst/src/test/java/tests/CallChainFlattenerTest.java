package tests;

import com.hirshi001.constcheck.CallChainFlattener;
import com.hirshi001.constcheck.ConstRules;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;
import util.JavaSourceFromString;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CallChainFlattenerTest {

    @Test
    public void testFlattenCollectsCallChainInOrder() throws Exception {
        String source = """
                package test;

                import com.hirshi001.constcheck.Const;

                class A {

                    @Const A m1(int x, int y) { return this; }
                    A m2() { return this; }
                    A m3() { return this; }
                }

                class B {
                    void run() {
                        A a = new A();
                        @Const A constA = a;
                        constA.m1(1+2, 3).m2().m3();
                    }
                }
                """;

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system compiler available (use JDK, not JRE)");
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            JavaSourceFromString fileObject = new JavaSourceFromString("test.A", source);
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    null,
                    Arrays.asList("-classpath", System.getProperty("java.class.path")),
                    null,
                    List.of(fileObject)
            );

            Iterable<? extends CompilationUnitTree> units = task.parse();
            task.analyze();

            List<MethodInvocationTree> invocations = new ArrayList<>();
            List<VariableElement> variables = new ArrayList<>();
            for (CompilationUnitTree unit : units) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                        invocations.add(node);
                        return super.visitMethodInvocation(node, unused);
                    }

                    @Override
                    public Void visitVariable(VariableTree node, Void unused) {
                        TreePath path = getCurrentPath();
                        Element element = Trees.instance(task).getElement(path);
                        if (element instanceof VariableElement variableElement) {
                            variables.add(variableElement);
                        }
                        return super.visitVariable(node, unused);
                    }
                }.scan(unit, null);
            }


            MethodInvocationTree rootChainCall = invocations.stream()
                    .filter(node -> node.toString().equals("constA.m1(1 + 2, 3).m2().m3()"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(rootChainCall, "Expected to find chained invocation constA.m1().m2().m3()");

            List<CallChainFlattener.CallStep> steps =
                    CallChainFlattener.flatten(rootChainCall, Trees.instance(task), task.getTypes());

            System.out.println(steps);


            assertEquals(3, steps.size(), "Expected m1 -> m2 -> m3");
            assertEquals("m1", steps.get(0).method().getSimpleName().toString());
            assertEquals("m2", steps.get(1).method().getSimpleName().toString());
            assertEquals("m3", steps.get(2).method().getSimpleName().toString());

            assertEquals(false, ConstRules.isConstMethod(steps.get(0).method()));
            assertEquals(false, ConstRules.isConstMethod(steps.get(1).method()));
            assertEquals(false, ConstRules.isConstMethod(steps.get(2).method()));

            assertEquals(true, ConstRules.isConstReturnType(steps.get(0).method()));

            VariableElement constAElement = variables.stream()
                    .filter(v -> v.getSimpleName().contentEquals("constA"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(constAElement, "Expected to find local variable constA");
            assertEquals(true, ConstRules.isConstVariable(constAElement));
        }
    }

    @Test
    public void testFlattenHandlesNestedMethodCalls() throws Exception {
        String source = """
                package test;

                class Chain {
                    Chain t2(Chain input) { return this; }
                    Chain b() { return this; }
                    Chain c() { return this; }
                }

                class B {
                    Chain a(Chain input) { return input; }
                    Chain t1() { return new Chain(); }
                    Chain x() { return new Chain(); }

                    void runNested() {
                        a(t1().t2(x())).b().c();
                    }
                }
                """;

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system compiler available (use JDK, not JRE)");
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            JavaSourceFromString fileObject = new JavaSourceFromString("test.B", source);
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    null,
                    Arrays.asList("-classpath", System.getProperty("java.class.path")),
                    null,
                    List.of(fileObject)
            );

            Iterable<? extends CompilationUnitTree> units = task.parse();
            task.analyze();

            List<MethodInvocationTree> invocations = new ArrayList<>();
            for (CompilationUnitTree unit : units) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                        invocations.add(node);
                        return super.visitMethodInvocation(node, unused);
                    }
                }.scan(unit, null);
            }

            MethodInvocationTree outerCall = invocations.stream()
                    .filter(node -> node.toString().equals("a(t1().t2(x())).b().c()"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(outerCall, "Expected to find nested outer invocation");

            List<CallChainFlattener.CallStep> outerSteps =
                    CallChainFlattener.flatten(outerCall, Trees.instance(task), task.getTypes());
            System.out.println(outerSteps);
            assertEquals(3, outerSteps.size(), "Expected a -> b -> c");
            assertEquals("a", outerSteps.get(0).method().getSimpleName().toString());
            assertEquals("b", outerSteps.get(1).method().getSimpleName().toString());
            assertEquals("c", outerSteps.get(2).method().getSimpleName().toString());

            MethodInvocationTree nestedArgCall = invocations.stream()
                    .filter(node -> node.toString().equals("t1().t2(x())"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(nestedArgCall, "Expected to find nested argument invocation");

            List<CallChainFlattener.CallStep> nestedSteps =
                    CallChainFlattener.flatten(nestedArgCall, Trees.instance(task), task.getTypes());
            assertEquals(2, nestedSteps.size(), "Expected t1 -> t2");
            assertEquals("t1", nestedSteps.get(0).method().getSimpleName().toString());
            assertEquals("t2", nestedSteps.get(1).method().getSimpleName().toString());
        }
    }
}
