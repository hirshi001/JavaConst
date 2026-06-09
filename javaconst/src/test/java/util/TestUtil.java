package util;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.tools.DiagnosticCollector;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

public class TestUtil {

    public static record TaskResult(StringWriter output, DiagnosticCollector<JavaFileObject> diagnostics, JavaCompiler.CompilationTask task, StandardJavaFileManager fileManager) implements AutoCloseable {
      
        @Override
        public void close() throws Exception {
            fileManager.close();
        }
    }

    public static TaskResult compile(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system compiler available (use JDK, not JRE)");
        }

        StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(null, null, null);

        JavaSourceFromString fileObject =
                new JavaSourceFromString("test.A", source);

        StringWriter output = new StringWriter();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        JavaCompiler.CompilationTask task = compiler.getTask(
                output,
                fileManager,
                diagnostics,
                Arrays.asList(
                        "-classpath", System.getProperty("java.class.path"),
                        "-Xplugin:ConstPlugin"
                ),
                null,
                Collections.singletonList(fileObject)
        );
        return new TaskResult(output, diagnostics, task, fileManager);
    }
}
