package com.hirshi001.example;

import com.hirshi001.constcheck.Const;
import com.hirshi001.constcheck.ConstFun;

public class TestClass {

    int x;
    TestClass next;

    public TestClass(int x) {
        this.x = x;
    }

    // Const method: safe to call on @Const receivers.
    @ConstFun
    @Const
    int getX() {
        return x;
    }

    // Mutable method: should not be callable on @Const receivers.
    void setX(int x) {
        this.x = x;
    }

    void setNext(TestClass next) {
        this.next = next;
    }

    // Non-const method for demonstrating nested-call argument failure.
    TestClass getNextMutable() {
        return next;
    }

    @ConstFun
    @Const
    TestClass getNextConst() {
        return next;
    }
}
