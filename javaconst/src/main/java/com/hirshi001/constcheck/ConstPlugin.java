package com.hirshi001.constcheck;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

public class ConstPlugin implements Plugin {
    @Override
    public String getName() {
        return "ConstPlugin";
    }

    @Override
    public void init(JavacTask task, String... strings) {
        task.addTaskListener(new ConstTaskListener(task));
    }
}
