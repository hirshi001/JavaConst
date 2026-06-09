package com.hirshi001.example;

import com.hirshi001.constcheck.Const;

public class Main {

    public static void main(String[] args) {
        TestClass root = new TestClass(1);
        TestClass child = new TestClass(2);

        // Normal mutable calls on non-const refs.
        // Field assignment is allowed in regular methods like setX/setNext.
        root.setX(10);
        root.setNext(child);
        child.setNext(child);
        int rootValue = root.getX();
        System.out.println(rootValue);

        @Const TestClass constRoot = root;

        // @ConstFun read-only call on @Const receiver (passes).
        int value = constRoot.getX();
        System.out.println(value);

        // @ConstFun local-variable assignment (passes).
        int adjusted = constRoot.getXWithLocalWork();
        System.out.println(adjusted);

        // @ConstFun may update @Mut fields and call other @ConstFun methods on this.
        int bumped = constRoot.bumpScratch();
        System.out.println(bumped);
        int twice = constRoot.readTwice();
        System.out.println(twice);

        // @ConstFun may call non-@ConstFun methods on another instance.
        int otherValue = constRoot.readOther(child);
        System.out.println(otherValue);

        // @Const return value must be stored in a @Const variable.
        @Const Integer constValue = constRoot.getXConst();

        // Method-parameter constness (passes).
        consumeConst(constRoot);

        // Nested invocation where terminal call is const-safe (passes).
        consumeConst(constRoot.getNextConst().getNextConst());

        // Uncomment to see receiver constness failure:
        // constRoot.setX(3);

        // Allowed: non-@Const argument to @Const parameter.
        consumeConst(root.getNextMutable());

        // Uncomment to see const-parameter mismatch:
        // consumeMutable(root.getNextConst());

        // Assignment examples:
        @Const TestClass constAssigned = root.getNextConst(); // allowed
        TestClass mutableAssigned = root.getNextMutable(); // allowed

        // Uncomment to see assignment mismatch:
        // mutableAssigned = constAssigned;

        // See commented examples in TestClass for failures:
        // - badFieldWrite(): assign to non-@Mut field inside @ConstFun
        // - badInternalCall(): call non-@ConstFun this method inside @ConstFun
        // - badStaticConstFun(): static @ConstFun method
        // - badPrimitiveConstFun(): primitive @ConstFun return type
        // - badStaticMutField: @Mut on a static field

        consumeConst(constAssigned);
        consumeMutable(mutableAssigned);
    }

    static void consumeConst(@Const TestClass testClass) {
        int value = testClass.getX();
        System.out.println(value);
    }

    static void consumeMutable(TestClass testClass) {
        int value = testClass.getX();
        System.out.println(value);
    }
}
