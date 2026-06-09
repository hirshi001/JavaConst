package tests;

import com.hirshi001.constcheck.ConstScanner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.JavaSourceFromString;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstScannerTest {

    private static final Logger log = LoggerFactory.getLogger(ConstScannerTest.class);

    private record ParsedUnit(JavacTask task, CompilationUnitTree unit, ConstScanner scanner) {}

    private static ParsedUnit parse(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system compiler available (use JDK, not JRE)");
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            JavaSourceFromString fileObject = new JavaSourceFromString("test.Wrapper", source);
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of("-classpath", System.getProperty("java.class.path")),
                    null,
                    List.of(fileObject)
            );

            Iterable<? extends CompilationUnitTree> units = task.parse();
            task.analyze();

            CompilationUnitTree unit = units.iterator().next();
            ConstScanner scanner = new ConstScanner(Trees.instance(task), task.getTypes(), unit);
            return new ParsedUnit(task, unit, scanner);
        }
    }

    private static Map<String, TreePath> localVariablePaths(CompilationUnitTree unit, JavacTask task) {
        Map<String, TreePath> paths = new HashMap<>();
        Trees trees = Trees.instance(task);

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void unused) {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof VariableElement variableElement) {
                    paths.put(variableElement.getSimpleName().toString(), getCurrentPath());
                }
                return super.visitVariable(node, unused);
            }
        }.scan(unit, null);

        return paths;
    }

    @Test
    public void isDirectlyInsideConstFunMethodReturnsTrueInsideAnnotatedMethod() throws Exception {
        String source = """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    int value;

                    @ConstFun
                    Integer getValue() {
                        int insideConstFun = value;
                        return insideConstFun;
                    }

                    Integer regular() {
                        int outsideConstFun = value;
                        return outsideConstFun;
                    }
                }
                """;

        ParsedUnit parsed = parse(source);
        Map<String, TreePath> variablePaths = localVariablePaths(parsed.unit(), parsed.task());

        TreePath insideConstFunPath = variablePaths.get("insideConstFun");
        assertNotNull(insideConstFunPath, "Expected local variable insideConstFun");

        boolean result = parsed.scanner().isDirectlyInsideConstFunMethod(insideConstFunPath);
        log.info("insideConstFun isDirectlyInsideConstFunMethod: {}", result);
        assertTrue(result);
    }

    @Test
    public void isDirectlyInsideConstFunMethodReturnsFalseOutsideAnnotatedMethod() throws Exception {
        String source = """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    int value;

                    @ConstFun
                    Integer getValue() {
                        int insideConstFun = value;
                        return insideConstFun;
                    }

                    Integer regular() {
                        int outsideConstFun = value;
                        return outsideConstFun;
                    }
                }
                """;

        ParsedUnit parsed = parse(source);
        Map<String, TreePath> variablePaths = localVariablePaths(parsed.unit(), parsed.task());

        TreePath outsideConstFunPath = variablePaths.get("outsideConstFun");
        assertNotNull(outsideConstFunPath, "Expected local variable outsideConstFun");

        boolean result = parsed.scanner().isDirectlyInsideConstFunMethod(outsideConstFunPath);
        log.info("outsideConstFun isDirectlyInsideConstFunMethod: {}", result);
        assertFalse(result);
    }

    @Test
    public void isDirectlyInsideConstFunMethodReturnsFalseForNestedLocalClassMethod() throws Exception {
        String source = """
                package test;

                import com.hirshi001.constcheck.ConstFun;

                class Wrapper {
                    @ConstFun
                    void outer() {
                        class Inner {
                            void innerMethod() {
                                int nestedInside = 0;
                            }
                        }
                    }
                }
                """;

        ParsedUnit parsed = parse(source);
        Map<String, TreePath> variablePaths = localVariablePaths(parsed.unit(), parsed.task());

        TreePath nestedInsidePath = variablePaths.get("nestedInside");
        assertNotNull(nestedInsidePath, "Expected local variable nestedInside");

        boolean result = parsed.scanner().isDirectlyInsideConstFunMethod(nestedInsidePath);
        log.info("nestedInside isDirectlyInsideConstFunMethod: {}", result);
        assertFalse(result);
    }
}
