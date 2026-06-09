package com.hirshi001.example;

import com.hirshi001.constcheck.Const;
import com.hirshi001.constcheck.ConstFun;
import com.hirshi001.constcheck.Mut;

public class TestClass {

    int x;
    @Mut
    int scratch;
    TestClass next;

    public TestClass(int x) {
        this.x = x;
    }

    // @ConstFun read-only accessor: does not mutate fields.
    // @ConstFun does not imply a @Const return value.
    @ConstFun
    Integer getX() {
        return x;
    }

    // @ConstFun with an explicit @Const return value.
    @ConstFun
    @Const
    Integer getXConst() {
        return x;
    }

    // @ConstFun may assign to locals, but not to unmarked this/super fields.
    @ConstFun
    Integer getXWithLocalWork() {
        int local = x;
        local = local + 1;
        return local;
    }

    // @ConstFun may assign to @Mut fields.
    @ConstFun
    Integer bumpScratch() {
        this.scratch = scratch + 1;
        return scratch;
    }

    // @ConstFun may call other @ConstFun methods on this.
    @ConstFun
    Integer readTwice() {
        return getX() + getX();
    }

    // @ConstFun may call non-@ConstFun methods on another instance.
    @ConstFun
    Integer readOther(TestClass other) {
        other.setX(other.getX() + 1);
        return other.getX();
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

    // Uncomment to see @ConstFun field-assignment rule failure:
    @ConstFun
    Integer badFieldWrite() {
        this.x = 0;
        return x;
    }

    // Uncomment to see @ConstFun internal-call rule failure:
    @ConstFun
    Integer badInternalCall() {
        setX(0);
        return getX();
    }

    // Uncomment to see @ConstFun static-method rule failure:
    @ConstFun
    static TestClass badStaticConstFun(TestClass input) {
        return input;
    }

    // Uncomment to see @ConstFun primitive-return rule failure:
    @ConstFun
    int badPrimitiveConstFun() {
        return x;
    }

    // Uncomment to see @Mut static-field rule failure:
    @Mut
    static int badStaticMutField;
}
