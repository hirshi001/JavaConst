package com.hirshi001.example;

import com.hirshi001.constcheck.Const;

public class Main {

    public static void main(String[] args) {
        TestClass root = new TestClass(1);
        TestClass child = new TestClass(2);

        // Normal mutable calls on non-const refs.
        root.setX(10);
        root.setNext(child);
        child.setNext(child);
        int rootValue = root.getX();
        System.out.println(rootValue);

        @Const TestClass constRoot = root;

        // Normal const-safe call on @Const receiver.
        int value = constRoot.getX();
        System.out.println(value);

        // Method-parameter constness (passes).
        consumeConst(constRoot);

        // Nested invocation where terminal call is const-safe (passes).
        consumeConst(constRoot.getNextConst().getNextConst());

        // Uncomment to see receiver constness failure:
        // constRoot.setX(3);

        // Allowed: non-@Const argument to @Const parameter.
        consumeConst(root.getNextMutable());

        // Uncomment to see const-parameter mismatch with new rule:
        // consumeMutable(root.getNextConst());

        // Assignment examples:
        @Const TestClass constAssigned = root.getNextConst(); // allowed
        TestClass mutableAssigned = root.getNextMutable(); // allowed

        // Uncomment to see assignment mismatch with new rule:
        // mutableAssigned = constAssigned;

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
