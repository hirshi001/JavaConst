package com.hirshi001.constcheck;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import javax.lang.model.util.Types;

public class ConstTaskListener implements TaskListener {
    private final Trees trees;
    private final Types types;

    public ConstTaskListener(JavacTask task) {
        this.trees = Trees.instance(task);
        this.types = task.getTypes();
    }

    @Override
    public void started(TaskEvent e) {
        TaskListener.super.started(e);
    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.ANALYZE) {
            ConstScanner scanner = new ConstScanner(trees, types, e.getCompilationUnit());
            scanner.scan(e.getCompilationUnit(), null);
        }
    }
}
